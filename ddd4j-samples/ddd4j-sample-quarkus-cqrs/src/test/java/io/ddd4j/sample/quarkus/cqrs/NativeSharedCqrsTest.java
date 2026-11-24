package io.ddd4j.sample.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.panache.PanacheProjectionPositionEntity;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.CqrsOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.quarkus.cqrs.shared.SharedOrderStorage;
import io.ddd4j.sample.quarkus.cqrs.shared.OrderEventSource;
import io.ddd4j.sample.quarkus.cqrs.shared.OrderOutboxRelay;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 PostgreSQL、HTTP、native CDI/JTA/source-aware Runner 及非原子资源恢复。 */
@QuarkusTest
@QuarkusTestResource(OrderDatabaseResource.class)
class NativeSharedCqrsTest {
    @Inject SharedOrderStorage storage;
    @Inject OrderEventSource source;
    @Inject OrderOutboxRelay relay;
    @Inject ViewManager views;
    @Inject ProjectionPositionRepository positions;
    @Inject io.quarkus.scheduler.Scheduler scheduler;
    @Inject EntityManager manager;
    @Inject EntityManagerFactory factory;
    @Inject TransactionSynchronizationRegistry transactions;
    @TestHTTPResource URI base;

    @Test void durableSourceProjectionFailureRetryAndSparsePosition() {
        scheduler.pause();
        views.stop();
        Driver driver = new Driver();
        SampleContractAssertions.cqrsOrder(driver, "quarkus-persistent");
        String id = driver.create(new CreateOrderCommand("quarkus-gap", "buyer-1", "Alice"));
        driver.addLine(new AddOrderLineCommand(id, "goods-1", "DDD Book", 2, new BigDecimal("59.90")));
        driver.pay(id, "gap-payment");
        assertThrows(IllegalStateException.class, () -> storage.transaction().execute(() -> {
            relay.relay();
            throw new IllegalStateException("event JTA committed but local Outbox acknowledgement rolls back");
        }));
        assertEquals(3, source.events(id).size(), "event append survives local acknowledgement rollback");
        storage.transaction().execute(() -> assertEquals(3L,
                storage.outbox().pending(100).stream().filter(message -> message.aggregateId().equals(id)).count()));
        relay.relay();
        assertEquals(3, source.events(id).size(), "same original Outbox IDs do not append duplicate events");
        storage.transaction().execute(() -> assertEquals(0L,
                storage.outbox().pending(100).stream().filter(message -> message.aggregateId().equals(id)).count()));
        long tail = QuarkusTransaction.requiringNew().call(() -> {
            var event = manager.createQuery("select e from StoredEventEntity e order by e.position desc", StoredEventEntity.class)
                    .setMaxResults(1).getSingleResult();
            event.setPosition(event.getPosition() + 17);
            return event.getPosition();
        });
        long prior = driver.position();
        AtomicBoolean failCheckpoint = new AtomicBoolean(true);
        factory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getServiceRegistry()
                .getService(org.hibernate.event.service.spi.EventListenerRegistry.class)
                .appendListeners(org.hibernate.event.spi.EventType.PRE_UPDATE,
                    (org.hibernate.event.spi.PreUpdateEventListener) event -> {
                        if (event.getEntity() instanceof PanacheProjectionPositionEntity && failCheckpoint.getAndSet(false)) {
                            throw new IllegalStateException("after query commit before checkpoint commit");
                        }
                        return false;
                    });
        assertThrows(IllegalStateException.class, driver::project);
        manager.clear();
        assertEquals(prior, driver.position());
        assertTrue(driver.projection(id).isPresent(), "local query commit survives JTA checkpoint failure");
        driver.project();
        assertEquals(tail+1, driver.position(), "source cursor includes actual position gaps");
        assertEquals(3, source.events(id).size());
        driver.project();
        assertEquals(tail+1, driver.position());
        assertEquals(new BigDecimal("119.80"), driver.find(id).totalAmount());
        assertMissingWriteModelCanBeRetried(driver);
        assertProjectionFiltersByAggregateType(driver);
    }

    private void assertMissingWriteModelCanBeRetried(Driver driver) {
        String id = driver.create(new CreateOrderCommand("quarkus-missing-write", "buyer-1", "Alice"));
        String temporaryId = id + "-fixture";
        long previous = driver.position();
        changeOrderId(id, temporaryId);
        try {
            given().baseUri(base.toString())
                    .contentType("application/json").body("null")
                    .when().post("/sample/projections/orders/run")
                    .then().statusCode(404)
                    .body("code", equalTo(404))
                    .body("msg", equalTo("No value present"));
            assertEquals(previous, driver.position());
            assertFalse(driver.projection(id).isPresent());
        } finally {
            changeOrderId(temporaryId, id);
        }
        driver.project();
        assertTrue(driver.position() > previous);
        assertEquals(id, driver.find(id).id());
    }

    private void changeOrderId(String originalId, String replacementId) {
        storage.transaction().execute(() -> {
            try (PreparedStatement statement = storage.transaction().connection().prepareStatement(
                    "UPDATE sample_orders SET id = ? WHERE id = ?")) {
                statement.setString(1, replacementId);
                statement.setString(2, originalId);
                assertEquals(1, statement.executeUpdate());
            } catch (SQLException failure) {
                throw new IllegalStateException("Unable to move the test order", failure);
            }
        });
    }

    private void assertProjectionFiltersByAggregateType(Driver driver) {
        long foreignOnly = appendFixtureEvent("Unrelated", "foreign-only");
        driver.project();
        assertEquals(foreignOnly + 1, driver.position());

        String orderId = driver.create(new CreateOrderCommand("quarkus-mixed-stream", "buyer-1", "Alice"));
        relay.relay();
        long mixedTail = appendFixtureEvent("Unrelated", "foreign-after-order");
        driver.project();
        assertEquals(orderId, driver.find(orderId).id());
        assertEquals(mixedTail + 1, driver.position());

        appendFixtureEvent("Order", "unsupported-order-envelope");
        given().baseUri(base.toString())
                .contentType("application/json").body("null")
                .when().post("/sample/projections/orders/run")
                .then().statusCode(500);
        assertEquals(mixedTail + 1, driver.position());
    }

    private long appendFixtureEvent(String aggregateType, String aggregateId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            JpaEventStore store = JpaEventStore.participatingManaged(manager, transactions::setRollbackOnly);
            AggregateRootId id = new AggregateRootId() {
                @Override public EntityType getType() { return new StringEntityType(aggregateType); }
                @Override public String asString() { return aggregateId; }
                @Override public String asTypedString() { return aggregateType + ":" + aggregateId; }
            };
            store.append(aggregateType, id, Collections.singletonList(new UnrelatedFixtureEvent()), 0L);
            return store.read(aggregateType, id).get(0).position();
        });
    }

    /** 事件仓储可以回读的夹具，用于校验订单投影的聚合边界。 */
    public static final class UnrelatedFixtureEvent extends DomainEvent<StringEntityId> {
        private static final long serialVersionUID = 1L;

        public UnrelatedFixtureEvent() {
            super("fixture");
        }
    }

    private final class Driver extends HttpOrderSampleDriver implements CqrsOrderSampleDriver {
        private String latestId;
        private boolean fail;
        Driver() { super(base.toString().replaceAll("/$", "") + "/sample"); }
        @Override public String create(CreateOrderCommand command) { latestId = super.create(command); return latestId; }
        @Override public OrderReadModel persisted(String id) { return storage.persisted(id); }
        @Override public List<OutboxMessage> events(String id) { relay.relay(); return source.events(id); }
        @Override public Optional<OrderReadModel> projection(String id) { return storage.queries().findProjectionById(id); }
        @Override public long position() { return QuarkusTransaction.requiringNew().call(() -> positions.findByStreamId("quarkus-orders").map(p -> p.getNextEventNumber()).orElse(0L)); }
        @Override public void failNextProjection() { fail = true; }
        private void setStatus(String value) {
            storage.transaction().execute(() -> {
                try (var sql = storage.transaction().connection().prepareStatement("UPDATE sample_orders SET status=? WHERE id=?")) {
                    sql.setString(1, value); sql.setString(2, latestId); sql.executeUpdate();
                } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            });
        }
        @Override public void project() {
            boolean failing = fail; fail = false;
            if (failing) { setStatus("S03_INVALID_STATUS"); }
            try { request("POST", "/projections/orders/run", null, String.class); }
            finally { if (failing) { setStatus("PAID"); } }
        }
    }
}
