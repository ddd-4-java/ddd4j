package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import jakarta.persistence.EntityManager;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;
import io.ddd4j.sample.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.testkit.CqrsOrderSampleDriver;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真正 PostgreSQL/HTTP 的契约、两个跨库失败窗口及关闭重开持久化验收。 */
class SpringCqrsHttpPersistenceTest {
    @Test
    void durableSourceProjectionRetryAndReopen() {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            String orderId;
            long committedPosition;
            ConfigurableApplicationContext context = start(postgres, "create");
            try (context) {
                assertEquals(1, context.getBeanNamesForType(CommandBus.class).length);
                var driver = driver(context);
                SampleContractAssertions.cqrsOrder(driver, "spring-cqrs-shared");
                orderId = driver.create(new CreateOrderCommand("spring-cqrs-crash", "buyer-1", "Alice"));
                driver.addLine(new AddOrderLineCommand(orderId, "goods-1", "DDD Book", 2, new BigDecimal("59.90")));
                driver.pay(orderId, "crash-payment");
                var failures = context.getBean(Faults.class);
                var projection = context.getBean(OrderProjection.class);
                long prior = projection.position();
                failures.afterAppend.set(true);
                assertThrows(IllegalStateException.class, driver::project);
                assertEquals(1, projection.events(orderId).size(), "source append committed before Outbox acknowledgement failed");
                assertEquals(prior, projection.position());
                projection.relay();
                assertEquals(3, projection.events(orderId).size(), "retry must not duplicate original Outbox identity");
                assertEquals(3, projection.events(orderId).stream().map(OutboxMessage::id).distinct().count());
                failures.checkpoint.set(true);
                assertThrows(IllegalStateException.class, driver::project);
                assertEquals(prior, projection.position(), "checkpoint transaction must roll back");
                assertTrue(driver.projection(orderId).isPresent(), "query write committed before checkpoint failure");
                driver.project();
                committedPosition = projection.position();
                assertTrue(committedPosition > prior);
                assertEquals(1, driver.query(new OrderQuery("buyer-1", null, 1, 100)).stream()
                        .filter(row -> row.id().equals(orderId)).count());
            }
            assertFalse(context.isActive());
            ConfigurableApplicationContext reopened = start(postgres, "validate");
            try (reopened) {
                var projection = reopened.getBean(OrderProjection.class);
                assertEquals(committedPosition, projection.position());
                assertEquals(3, projection.events(orderId).size());
                assertEquals("119.80", driver(reopened).find(orderId).totalAmount().toPlainString());
                driver(reopened).project();
                assertEquals(committedPosition, projection.position());
            }
            assertFalse(reopened.isActive());
        }
    }

    @Test
    void sourcePagesConsumeOnlyOrdersAndRetainPriorStateOnFailure() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            ConfigurableApplicationContext context = start(postgres, "create");
            try {
                assertSourcePages(driver(context), context.getBean(OrderProjection.class),
                        context.getBean(EntityManagerFactory.class));
            } finally {
                context.close();
            }
        }
    }

    @Test
    void danglingOrderRollsBackProjectionThroughHttp() throws java.io.IOException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            try (ConfigurableApplicationContext context = start(postgres, "create")) {
                Driver driver = driver(context);
                OrderProjection projection = context.getBean(OrderProjection.class);
                ObjectMapper mapper = context.getBean(ObjectMapper.class).copy().findAndRegisterModules();
                String priorId = driver.create(new CreateOrderCommand("spring-orphan-prior", "buyer-1", "Alice"));
                driver.project();
                OrderReadModel prior = driver.find(priorId);
                long priorPosition = driver.position();

                String pendingId = driver.create(new CreateOrderCommand("spring-orphan-pending", "buyer-1", "Alice"));
                projection.relay();
                String missingId = "spring-orphan-missing";
                OrderOutboxEvent orphan = new OrderOutboxEvent("00000000-0000-0000-0000-000000000001", missingId, "OrderCreatedEvent",
                        mapper.valueToTree(new OrderCreatedEvent(missingId)));
                EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
                try (EntityManager resource = factory.createEntityManager()) {
                    JpaEventStore store = new JpaEventStore(resource);
                    OrderStreamId stream = new OrderStreamId(missingId);
                    store.append("Order", stream, Collections.singletonList(orphan), 0);
                    long orphanPosition = store.read("Order", stream).get(0).position();
                    assertTrue(orphanPosition > priorPosition);
                }
                assertFalse(context.getBean(OrderRepository.class).findById(missingId).isPresent());
                assertEquals(missingId, projection.events(missingId).get(0).aggregateId());

                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                SampleHttpTransport.Response response = SampleHttpTransport.request(
                        "http://127.0.0.1:" + port + "/projections/orders/run", "POST",
                        Collections.emptyMap(), Collections.emptyMap(), null);
                System.out.println("DANGLING_PROJECTION_HTTP " + response.statusCode() + " " + response.body());
                assertEquals(404, response.statusCode(), response.body());
                com.fasterxml.jackson.databind.JsonNode error = mapper.readTree(response.body());
                assertEquals(404, error.path("code").asInt());
                assertEquals("No value present", error.path("msg").asText());
                assertTrue(error.path("data").isNull(), response.body());
                assertEquals(false, error.path("ok").asBoolean());
                assertEquals(false, error.path("success").asBoolean());
                assertEquals(true, error.path("empty").asBoolean());
                assertEquals(priorPosition, driver.position());
                assertFalse(driver.projection(pendingId).isPresent());
                assertFalse(driver.projection(missingId).isPresent());
                assertEquals(prior, driver.find(priorId));
            }
        }
    }

    @Test
    void absentProjectionAndOutboxWiringUseNativeHttp() throws java.io.IOException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            EntityManagerFactory factory;
            try (ConfigurableApplicationContext context = start(postgres, "create")) {
                factory = context.getBean("entityManagerFactory", EntityManagerFactory.class);
                assertTrue(factory.isOpen());
                assertEquals(1, context.getBeanNamesForType(CommandBus.class).length);
                Driver driver = driver(context);
                ObjectMapper mapper = context.getBean(ObjectMapper.class);
                OrderRepository repository = context.getBean(OrderRepository.class);
                JdbcOutboxPort outbox = context.getBean("orderOutbox", JdbcOutboxPort.class);
                long initialPosition = driver.position();
                String id = driver.create(new CreateOrderCommand("spring-cqrs-native-query", "buyer-1", "Alice"));
                assertTrue(repository.findById(id).isPresent());
                assertMissingProjection(context, mapper, id);
                assertMissingProjection(context, mapper, "spring-absent-order");
                assertEquals(initialPosition, driver.position());
                assertFalse(driver.projection(id).isPresent());

                driver.project();
                assertEquals(id, driver.find(id).id());
                assertTrue(driver.position() > initialPosition);
                OutboxMessage source = context.getBean(OrderProjection.class).events(id).get(0);
                OutboxMessage stored = outbox.findById(source.id());
                assertEquals(stored.aggregateId(), source.aggregateId());
                assertEquals(OrderCreatedEvent.class.getName(), source.eventType());
                assertEquals(stored.eventType(), source.eventType());
                assertEquals(stored.payload(), source.payload());
                assertTrue(mapper.valueToTree(source.payload()).hasNonNull("event-timestamp"));

                Faults faults = context.getBean(Faults.class);
                faults.rejectOrderCommit.set(true);
                assertThrows(IllegalStateException.class, () -> driver.create(
                        new CreateOrderCommand("spring-cqrs-rolled-back", "buyer-1", "Alice")));
                OutboxMessage rejected = faults.rejectedOrderEvent.get();
                assertNotNull(rejected);
                assertFalse(repository.findByOrderNo("spring-cqrs-rolled-back").isPresent());
                assertFalse(repository.findById(rejected.aggregateId()).isPresent());
                assertThrows(IllegalStateException.class, () -> outbox.findById(rejected.id()));
                assertFalse(driver.projection(rejected.aggregateId()).isPresent());
            }
            assertFalse(factory.isOpen());
        }
    }

    @Test
    void emptyCommandResultsUseCommonMissingOrderMessage() {
        CommandBus emptyCommands = new CommandBus() {
            @Override public <R> Result<R> execute(Command command) {
                return Result.ok(null);
            }
        };
        OrderController controller = new OrderController(emptyCommands, null, null);
        assertEquals("No value present", assertThrows(NoSuchElementException.class, () ->
                controller.create(new CreateOrderCommand("order", "buyer-1", "Alice"))).getMessage());
        assertEquals("No value present", assertThrows(NoSuchElementException.class, () ->
                controller.addLine(new AddOrderLineCommand("missing", "goods-1", "DDD Book", 1, BigDecimal.ONE))).getMessage());
        assertEquals("No value present", assertThrows(NoSuchElementException.class, () ->
                controller.pay("missing", "payment-key")).getMessage());
    }

    private void assertMissingProjection(ConfigurableApplicationContext context, ObjectMapper mapper, String orderId)
            throws java.io.IOException {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        SampleHttpTransport.Response response = SampleHttpTransport.request(
                "http://127.0.0.1:" + port + "/orders/" + orderId, "GET",
                Collections.emptyMap(), Collections.emptyMap(), null);
        System.out.println("MISSING_PROJECTION_HTTP " + response.statusCode() + " " + response.body());
        assertEquals(404, response.statusCode(), response.body());
        JsonNode error = mapper.readTree(response.body());
        assertEquals(404, error.path("code").asInt(), response.body());
        assertEquals("No value present", error.path("msg").asText(), response.body());
        assertTrue(error.path("data").isNull(), response.body());
    }

    private void assertSourcePages(Driver driver, OrderProjection projection, EntityManagerFactory factory) {
        String priorId = driver.create(new CreateOrderCommand("spring-prior", "buyer-1", "Alice"));
        driver.project();
        OrderReadModel prior = driver.find(priorId);

        long foreignTail = appendFixtureEvent(factory, "Unrelated", "foreign-only");
        driver.project();
        assertEquals(foreignTail + 1, driver.position());
        assertEquals(prior, driver.find(priorId));

        String mixedId = driver.create(new CreateOrderCommand("spring-mixed", "buyer-1", "Alice"));
        projection.relay();
        long mixedTail = appendFixtureEvent(factory, "Unrelated", "foreign-after-order");
        driver.project();
        assertEquals(mixedId, driver.find(mixedId).id());
        assertEquals(mixedTail + 1, driver.position());

        String pendingId = driver.create(new CreateOrderCommand("spring-pending", "buyer-1", "Alice"));
        projection.relay();
        appendFixtureEvent(factory, "Order", "unsupported-order-envelope");
        assertThrows(IllegalStateException.class, driver::project);
        assertEquals(mixedTail + 1, driver.position());
        assertFalse(driver.projection(pendingId).isPresent());
        assertEquals(prior, driver.find(priorId));
    }

    private long appendFixtureEvent(EntityManagerFactory factory, String aggregateType, String value) {
        AggregateRootId id = new AggregateRootId() {
            @Override public EntityType getType() { return new StringEntityType(aggregateType); }
            @Override public String asString() { return value; }
            @Override public String asTypedString() { return aggregateType + ":" + value; }
        };
        try (EntityManager manager = factory.createEntityManager()) {
            JpaEventStore store = new JpaEventStore(manager);
            store.append(aggregateType, id, Collections.singletonList(new SourcePageFixtureEvent()), 0);
            return store.read(aggregateType, id).get(0).position();
        }
    }

    /** 可持久化的非订单载荷，用于分别验证聚合过滤及订单载荷校验。 */
    public static final class SourcePageFixtureEvent extends DomainEvent<StringEntityId> {
        private static final long serialVersionUID = 1L;

        public SourcePageFixtureEvent() {
            super("fixture");
        }
    }

    private ConfigurableApplicationContext start(PostgreSQLContainer<?> postgres, String schema) {
        return SpringApplication.run(SpringCqrsSampleApplication.class, "--server.port=0",
                "--sample.jdbc.url=" + postgres.getJdbcUrl(), "--sample.jdbc.username=" + postgres.getUsername(),
                "--sample.jdbc.password=" + postgres.getPassword(), "--sample.jpa.schema-action=" + schema);
    }

    private Driver driver(ConfigurableApplicationContext context) {
        return new Driver(context);
    }

    private static class Driver extends HttpOrderSampleDriver implements CqrsOrderSampleDriver {
        private final OrderProjection runner;
        private final OrderReadModelPort queries;
        private final Faults faults;
        Driver(ConfigurableApplicationContext context) {
            super("http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort());
            runner = context.getBean(OrderProjection.class);
            queries = context.getBean(OrderReadModelPort.class);
            faults = context.getBean(Faults.class);
        }
        @Override public OrderReadModel persisted(String id) { return runner.persisted(id); }
        @Override public List<OutboxMessage> events(String id) { runner.relay(); return runner.events(id); }
        @Override public Optional<OrderReadModel> projection(String id) { return queries.findProjectionById(id); }
        @Override public long position() { return runner.position(); }
        @Override public void failNextProjection() { faults.beforeQuery.set(true); }
        @Override public void project() { request("POST", "/projections/orders/run", null, String.class); }
    }

    /** 只在测试 DI 中替换边界以注入故障，不增加产品调试入口。 */
    @Configuration(proxyBeanMethods = false)
    static class Faults {
        final AtomicBoolean afterAppend = new AtomicBoolean();
        final AtomicBoolean rejectOrderCommit = new AtomicBoolean();
        final AtomicReference<OutboxMessage> rejectedOrderEvent = new AtomicReference<>();
        final AtomicBoolean beforeQuery = new AtomicBoolean();
        final AtomicBoolean checkpoint = new AtomicBoolean();
        @Bean @Primary
        OutboxPort faultyOutbox(@Qualifier("orderOutbox") OutboxPort delegate) {
            return new OutboxPort() {
                @Override public void append(List<OutboxMessage> messages) {
                    try { delegate.append(messages); } catch (RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(Faults.class).error("actual Outbox append failure", failure);
                        throw failure;
                    }
                    if (rejectOrderCommit.getAndSet(false)) {
                        rejectedOrderEvent.set(messages.get(0));
                        throw new IllegalStateException("after JDBC outbox append");
                    }
                }
                @Override public List<OutboxMessage> pending(int limit) { return delegate.pending(limit); }
                @Override public void markPublished(String id) {
                    if (afterAppend.getAndSet(false)) { throw new IllegalStateException("after source append, before ack"); }
                    delegate.markPublished(id);
                }
                @Override public void markFailed(String id, String reason) { delegate.markFailed(id, reason); }
            };
        }
        @Bean @Primary
        OrderReadModelPort faultyQueries(@Qualifier("orderQueries") OrderReadModelPort delegate) {
            return new OrderReadModelPort() {
                @Override public void project(OrderReadModel order) {
                    if (beforeQuery.getAndSet(false)) { throw new IllegalStateException("before query write"); }
                    delegate.project(order);
                }
                @Override public Optional<OrderReadModel> findProjectionById(String id) { return delegate.findProjectionById(id); }
                @Override public List<OrderReadModel> query(OrderQuery query) { return delegate.query(query); }
            };
        }
        @Bean
        org.springframework.beans.factory.InitializingBean checkpointFailure(EntityManagerFactory factory) {
            return () -> {
                var registry = factory.unwrap(SessionFactoryImplementor.class).getServiceRegistry().getService(EventListenerRegistry.class);
                registry.appendListeners(EventType.PRE_INSERT, (PreInsertEventListener) event -> failCheckpoint(event.getEntity()));
                registry.appendListeners(EventType.PRE_UPDATE, (PreUpdateEventListener) event -> failCheckpoint(event.getEntity()));
            };
        }
        private boolean failCheckpoint(Object entity) {
            if (entity instanceof ProjectionPositionEntity && checkpoint.getAndSet(false)) {
                throw new IllegalStateException("after query commit, before checkpoint write");
            }
            return false;
        }
    }
}
