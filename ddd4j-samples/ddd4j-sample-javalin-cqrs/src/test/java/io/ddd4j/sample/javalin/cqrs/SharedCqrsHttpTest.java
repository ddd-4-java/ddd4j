package io.ddd4j.sample.javalin.cqrs;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.ddd4j.sample.javalin.cqrs.shared.OrderProjection;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import io.ddd4j.sample.javalin.cqrs.shared.SharedCqrsRuntime;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.CqrsOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

/** 实际 PostgreSQL、Javalin 与 Guice 显式投影，包含失败重试和关闭重开。 */
class SharedCqrsHttpTest {
    @Test
    void projectionEventTypesRemainEmptyForNullLookup() {
        OrderProjection projection = new OrderProjection(null, null, null, null, null, null);
        Collection<String> eventTypes = projection.view().getEventTypes();
        assertTrue(eventTypes.isEmpty());
        assertFalse(eventTypes.contains(null));
        assertThrows(UnsupportedOperationException.class, () -> eventTypes.remove(null));
    }

    @Test
    void durableSourceProjectionRetryAndReopen() {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=512m"))) {
            postgres.start();
            var source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl()); source.setUser(postgres.getUsername()); source.setPassword(postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            AtomicBoolean fail = new AtomicBoolean();
            var runtime = new SharedCqrsRuntime(source, "create", false, delegate -> new OrderReadModelPort() {
                @Override public void project(OrderReadModel order) {
                    if (fail.getAndSet(false)) { throw new IllegalStateException("before query write"); }
                    delegate.project(order);
                }
                @Override public Optional<OrderReadModel> findProjectionById(String id) { return delegate.findProjectionById(id); }
                @Override public List<OrderReadModel> query(OrderQuery query) { return delegate.query(query); }
            });
            String id;
            long position;
            try (var application = JavalinCqrsApplication.start(0, runtime)) {
                Driver driver = new Driver(application, runtime, fail);
                SampleContractAssertions.cqrsOrder(driver, "javalin-cqrs-shared");
                legacyRoutes(application);
                id = driver.create(new io.ddd4j.sample.order.application.CreateOrderCommand("javalin-gap", "buyer-1", "Alice"));
                String unprojectedId = id;
                assertAll(
                        () -> assertEquals("No value present", assertThrows(NoSuchElementException.class,
                                () -> runtime.projection().persisted("missing-order")).getMessage()),
                        () -> {
                            IllegalStateException failure = assertThrows(IllegalStateException.class,
                                    () -> driver.find(unprojectedId));
                            assertTrue(failure.getMessage().startsWith("HTTP 500: "), failure.getMessage());
                            assertTrue(failure.getMessage().contains("\"msg\":\"No value present\""),
                                    failure.getMessage());
                        });
                driver.addLine(new io.ddd4j.sample.order.application.AddOrderLineCommand(id, "goods-1", "DDD Book", 2, new java.math.BigDecimal("59.90")));
                driver.pay(id, "gap-payment");
                runtime.projection().relay();
                long sourceTail;
                // 隔离数据库夹具制造真实源位置空洞，验证绝不使用事件数量作为检查点。
                try (var manager = runtime.factory().createEntityManager()) {
                    manager.getTransaction().begin();
                    var tail = manager.createQuery("select e from StoredEventEntity e order by e.position desc",
                            io.ddd4j.data.event.store.jpa.StoredEventEntity.class).setMaxResults(1).getSingleResult();
                    sourceTail = tail.getPosition() + 17;
                    tail.setPosition(sourceTail);
                    manager.getTransaction().commit();
                }
                long prior = runtime.projection().position();
                AtomicBoolean checkpointFailure = new AtomicBoolean(true);
                var registry = runtime.factory().unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class)
                        .getServiceRegistry().getService(org.hibernate.event.service.spi.EventListenerRegistry.class);
                registry.appendListeners(org.hibernate.event.spi.EventType.PRE_UPDATE,
                        (org.hibernate.event.spi.PreUpdateEventListener) event -> {
                            if (event.getEntity() instanceof io.ddd4j.data.projection.jpa.ProjectionPositionEntity
                                    && checkpointFailure.getAndSet(false)) {
                                throw new IllegalStateException("after query commit, before checkpoint write");
                            }
                            return false;
                        });
                assertThrows(IllegalStateException.class, driver::project);
                assertEquals(prior, runtime.projection().position());
                assertTrue(driver.projection(id).isPresent(), "query commit survives checkpoint rollback");
                driver.project();
                assertEquals(sourceTail + 1, runtime.projection().position(), "persist actual sparse source cursor");
                position = runtime.projection().position();
                assertEquals(3, runtime.projection().events(id).size());
            }
            assertFalse(runtime.factory().isOpen());
            var reopened = new SharedCqrsRuntime(source, "validate", false, java.util.function.UnaryOperator.identity());
            try (var application = JavalinCqrsApplication.start(0, reopened)) {
                assertEquals(position, reopened.projection().position());
                assertEquals(3, reopened.projection().events(id).size());
                Driver driver = new Driver(application, reopened, fail);
                assertEquals("119.80", driver.find(id).totalAmount().toPlainString());
                driver.project();
                assertEquals(position, reopened.projection().position());
            }
            assertFalse(reopened.factory().isOpen());
        }
    }
    @Test
    void sourcePagesConsumeOnlyOrdersAndRetainPriorStateOnFailure() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"))) {
            postgres.start();
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl());
            source.setUser(postgres.getUsername());
            source.setPassword(postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            SharedCqrsRuntime runtime = new SharedCqrsRuntime(source, "create", false, UnaryOperator.identity());
            try (JavalinCqrsApplication.Application application = JavalinCqrsApplication.start(0, runtime)) {
                assertSourcePages(new Driver(application, runtime, new AtomicBoolean()), runtime.projection(),
                        runtime.factory());
            }
        }
    }

    private void assertSourcePages(Driver driver, OrderProjection projection, EntityManagerFactory factory) {
        String priorId = driver.create(new CreateOrderCommand("javalin-prior", "buyer-1", "Alice"));
        driver.project();
        OrderReadModel prior = driver.find(priorId);

        long foreignTail = appendFixtureEvent(factory, "Unrelated", "foreign-only");
        driver.project();
        assertEquals(foreignTail + 1, driver.position());
        assertEquals(prior, driver.find(priorId));

        String mixedId = driver.create(new CreateOrderCommand("javalin-mixed", "buyer-1", "Alice"));
        projection.relay();
        long mixedTail = appendFixtureEvent(factory, "Unrelated", "foreign-after-order");
        driver.project();
        assertEquals(mixedId, driver.find(mixedId).id());
        assertEquals(mixedTail + 1, driver.position());

        String pendingId = driver.create(new CreateOrderCommand("javalin-pending", "buyer-1", "Alice"));
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

    private void legacyRoutes(JavalinCqrsApplication.Application application) {
        var legacy = new io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver(
                "http://127.0.0.1:" + application.app().port(), "/api/orders", Map.of());
        String id = legacy.create(new io.ddd4j.sample.order.application.CreateOrderCommand("legacy-http", "buyer-legacy", "Bob"));
        legacy.addLine(new io.ddd4j.sample.order.application.AddOrderLineCommand(id, "sku-old", "Old book", 2, new java.math.BigDecimal("25")));
        legacy.pay(id, "ignored-old-header");
        var detail = legacy.request("GET", "/api/orders/query/detail/" + id, null, Map.of());
        assertEquals(1, detail.path("lineCount").asInt());
        assertEquals("PAID", detail.path("status").asText());
        assertEquals(0, new java.math.BigDecimal("50").compareTo(detail.path("totalAmount").path("amount").decimalValue()));
        assertEquals(1, legacy.request("GET", "/api/orders/query/stats", null, Map.of()).path("total").asInt());
        legacy.request("POST", "/api/goods", Map.of("code", "legacy-goods", "name", "Legacy goods", "price", 10, "stock", 4), Map.of());
        assertEquals("legacy-goods", legacy.request("GET", "/api/goods/query/by-code/legacy-goods", null, Map.of()).path("code").asText());
    }

    private static final class Driver extends HttpOrderSampleDriver implements CqrsOrderSampleDriver {
        private final SharedCqrsRuntime runtime;
        private final AtomicBoolean failure;
        Driver(JavalinCqrsApplication.Application app, SharedCqrsRuntime runtime, AtomicBoolean failure) {
            super("http://127.0.0.1:" + app.app().port() + "/sample"); this.runtime = runtime; this.failure = failure;
        }
        @Override public OrderReadModel persisted(String id) { return runtime.projection().persisted(id); }
        @Override public List<OutboxMessage> events(String id) { runtime.projection().relay(); return runtime.projection().events(id); }
        @Override public Optional<OrderReadModel> projection(String id) { return runtime.queries().findProjectionById(id); }
        @Override public long position() { return runtime.projection().position(); }
        @Override public void failNextProjection() { failure.set(true); }
        @Override public void project() { request("POST", "/projections/orders/run", null, String.class); }
    }
    @Test
    void closePreservesCallingThreadsCompleteContext() {
        try (var original = io.ddd4j.core.context.ThreadContext.open()) {
            io.ddd4j.core.context.ThreadContext.put(new Object(), new Object());
            io.ddd4j.core.context.ThreadContext.set("caller-value", "preserved");
            var expected = io.ddd4j.core.context.ThreadContext.getResources();
            JavalinCqrsApplication.start(0, null).close();
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getResources());
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getValues());
        }
    }

}
