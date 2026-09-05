package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.AsyncEventStore;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.r2dbc.h2.CloseableConnectionFactory;
import io.r2dbc.h2.H2ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2dbcEventStoreTest {

    private static final String ORDER_TYPE = "Order";

    private CloseableConnectionFactory connectionFactory;
    private AsyncEventStore eventStore;

    @BeforeEach
    void setUp() {
        // CloseableConnectionFactory 持有常驻会话，保证内存库在工厂存活期内不销毁
        connectionFactory = H2ConnectionFactory.inMemory("r2dbc-event-store-test");
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.close().block();
    }

    @Test
    void appendAndReadShouldRoundTripBusinessPayload() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-a");
        eventStore.append(ORDER_TYPE, orderId, reactor.core.publisher.Flux.fromIterable(Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed"))), 0).block();

        List<AsyncStoredEvent> events = eventStore.read(ORDER_TYPE, orderId).collectList().block();
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
                reactor.core.publisher.Flux.fromIterable(Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first"))), 0).block();

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        reactor.core.publisher.Flux.fromIterable(Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("second"))), 0).block());
        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
    }

    @Test
    void readVersionRangeShouldReturnInclusiveSlice() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-c");
        eventStore.append(ORDER_TYPE, orderId, reactor.core.publisher.Flux.fromIterable(Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("v1"), new OrderCreatedEvent("v2"), new OrderCreatedEvent("v3"))), 0).block();

        List<AsyncStoredEvent> slice = eventStore.read(ORDER_TYPE, orderId, 2, 3).collectList().block();
        assertEquals(2, slice.size());
        assertEquals(2L, slice.get(0).version());
        assertEquals(3L, slice.get(1).version());
    }

    @Test
    void readAllShouldPreserveGlobalPositionOrder() {
        TestAggregateRootId first = new TestAggregateRootId("order-d");
        TestAggregateRootId second = new TestAggregateRootId("order-e");
        eventStore.append(ORDER_TYPE, first,
                reactor.core.publisher.Flux.fromIterable(Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("d"))), 0).block();
        eventStore.append(ORDER_TYPE, second,
                reactor.core.publisher.Flux.fromIterable(Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("e"))), 0).block();

        List<AsyncStoredEvent> events = eventStore.readAll(1, 10).collectList().block();
        assertEquals(2, events.size());
        assertEquals(first.asString(), events.get(0).aggregateId().asString());
        assertEquals(second.asString(), events.get(1).aggregateId().asString());
        assertTrue(events.get(0).position() < events.get(1).position());
        assertEquals(1L, events.get(0).position());
        assertEquals(2L, events.get(1).position());
    }

    @Test
    void causalityColumnsShouldRoundTrip() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-f");
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        eventStore.append(ORDER_TYPE, orderId, reactor.core.publisher.Flux.fromIterable(Arrays.<DomainEvent<?>>asList(cause, effect)), 0).block();

        List<AsyncStoredEvent> events = eventStore.read(ORDER_TYPE, orderId).collectList().block();
        assertNull(events.get(0).correlationId());
        assertEquals(cause.getEventId(), events.get(1).correlationId());
        assertEquals(cause.getEventId(), events.get(1).causationId());
    }

    @Test
    void readMissingStreamShouldReturnEmpty() {
        assertEquals(0, eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing"))
                .collectList().block().size());
    }

    @Test
    void payloadColumnShouldUseTextType() {
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-g"),
                reactor.core.publisher.Flux.fromIterable(Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("g"))), 0).block();
        String dataType = Mono.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> Mono.from(connection.createStatement(
                                        "select data_type from information_schema.columns"
                                                + " where upper(table_name) = $1"
                                                + " and upper(column_name) = $2")
                                .bind(0, EventStoreConstants.TABLE_NAME.toUpperCase())
                                .bind(1, EventStoreConstants.COLUMN_PAYLOAD.toUpperCase())
                                .execute())
                                .flatMap(result -> Mono.from(result.map((row, metadata) ->
                                        row.get(0, String.class)))),
                        connection -> Mono.from(connection.close()))
                .block();
        // H2 2.2 将 TEXT 声明报告为 CHARACTER VARYING（PG 上为 text，容器轨另验）；
        // 断言语义：payload 落在字符类型族，而非数值/二进制
        assertTrue(dataType != null && dataType.startsWith("CHARACTER"));
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
