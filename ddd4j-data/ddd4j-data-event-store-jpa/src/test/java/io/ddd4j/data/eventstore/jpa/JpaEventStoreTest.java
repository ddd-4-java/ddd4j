package io.ddd4j.data.eventstore.jpa;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaEventStoreTest {

    private static final String ORDER_TYPE = "Order";

    private static EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:jpa-event-store-test;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.connection.username", "sa");
        configuration.setProperty("hibernate.connection.password", "");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.setProperty("hibernate.show_sql", "false");
        configuration.addAnnotatedClass(StoredEventEntity.class);
        entityManagerFactory = configuration.buildSessionFactory();
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    @AfterEach
    void tearDown() {
        EntityTransaction tx = entityManager.getTransaction();
        if (!tx.isActive()) {
            tx.begin();
        }
        entityManager.createQuery("delete from StoredEventEntity").executeUpdate();
        tx.commit();
        entityManager.close();
    }

    @Test
    void appendAndReadShouldRoundTripBusinessPayload() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-a");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed")), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
        assertEquals("created", ((OrderCreatedEvent) events.get(0).payload()).getFact());
        assertEquals("renamed", ((OrderCreatedEvent) events.get(1).payload()).getFact());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
    }

    @Test
    void appendWithStaleVersionShouldReject() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-b");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("second")), 0));
        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
    }

    @Test
    void readVersionRangeShouldReturnInclusiveSlice() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-c");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("v1"), new OrderCreatedEvent("v2"), new OrderCreatedEvent("v3")), 0);

        List<StoredEvent> slice = eventStore.read(ORDER_TYPE, orderId, 2, 3);
        assertEquals(2, slice.size());
        assertEquals(2L, slice.get(0).version());
        assertEquals(3L, slice.get(1).version());
    }

    @Test
    void readAllShouldPreserveGlobalPositionOrderAcrossAggregates() {
        TestAggregateRootId first = new TestAggregateRootId("order-d");
        TestAggregateRootId second = new TestAggregateRootId("order-e");
        eventStore.append(ORDER_TYPE, first,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("d")), 0);
        eventStore.append(ORDER_TYPE, second,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("e")), 0);

        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertTrue(events.size() >= 2);
        assertEquals(first.asString(), events.get(events.size() - 2).aggregateId().asString());
        assertEquals(second.asString(), events.get(events.size() - 1).aggregateId().asString());
        assertTrue(events.get(events.size() - 2).position() < events.get(events.size() - 1).position());
    }

    @Test
    void causalityColumnsShouldRoundTrip() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-f");
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(cause, effect), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertNull(events.get(0).correlationId());
        assertEquals(cause.getEventId(), events.get(1).correlationId());
        assertEquals(cause.getEventId(), events.get(1).causationId());
    }

    @Test
    void readMissingStreamShouldReturnEmpty() {
        assertEquals(0, eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size());
    }

    private static final class TestAggregateRootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");

        private final String value;

        private TestAggregateRootId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    /** 业务事件样例：无参构造 + JavaBean 属性（payload 序列化约定）。 */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        private String fact;

        public OrderCreatedEvent() {
            super();
        }

        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        private OrderCreatedEvent(String fact, io.ddd4j.core.ddd.event.Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.fact = fact;
        }

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
