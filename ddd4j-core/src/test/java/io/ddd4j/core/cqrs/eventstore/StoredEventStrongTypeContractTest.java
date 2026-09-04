package io.ddd4j.core.cqrs.eventstore;

import java.util.Objects;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link StoredEvent} 构造契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class StoredEventStrongTypeContractTest {

    private static final EventId EVENT_ID = new EventId();
    private static final String AGGREGATE_TYPE = "Order";
    private static final AggregateRootId AGGREGATE_ID = new TestAggregateRootId("order-1");
    private static final long VERSION = 3L;
    private static final long POSITION = 42L;
    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.now();
    private static final EventId CORRELATION_ID = new EventId();
    private static final EventId CAUSATION_ID = new EventId();

    static Stream<Arguments> nullMandatoryArguments() {
        return Stream.of(
                Arguments.of("eventId", 0),
                Arguments.of("aggregateType", 1),
                Arguments.of("aggregateId", 2),
                Arguments.of("timestamp", 3),
                Arguments.of("payload", 4));
    }

    @ParameterizedTest(name = "constructor rejects null {0}")
    @MethodSource("nullMandatoryArguments")
    void constructorRejectsNullMandatoryArgument(String argumentName, int nullIndex) {
        assertThrows(NullPointerException.class, () -> newStoredEventWithNullAt(nullIndex),
                "argument " + argumentName + " must be null-checked");
    }

    @Test
    void constructorAllowsNullCorrelationAndCausationId() {
        StoredEvent storedEvent = new StoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID,
                VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);

        assertNull(storedEvent.correlationId(), "correlationId should be nullable");
        assertNull(storedEvent.causationId(), "causationId should be nullable");
    }

    @Test
    void accessorsReturnConstructorValues() {
        DomainEvent<?> payload = new TestEvent();

        StoredEvent storedEvent = new StoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID,
                VERSION, POSITION, TIMESTAMP, payload, CORRELATION_ID, CAUSATION_ID);

        assertSame(EVENT_ID, storedEvent.eventId());
        assertEquals(AGGREGATE_TYPE, storedEvent.aggregateType());
        assertSame(AGGREGATE_ID, storedEvent.aggregateId());
        assertEquals(VERSION, storedEvent.version());
        assertEquals(POSITION, storedEvent.position());
        assertSame(TIMESTAMP, storedEvent.timestamp());
        assertSame(payload, storedEvent.payload());
        assertSame(CORRELATION_ID, storedEvent.correlationId());
        assertSame(CAUSATION_ID, storedEvent.causationId());
    }

    private static StoredEvent newStoredEventWithNullAt(int nullIndex) {
        switch (nullIndex) {
            case 0:
                return new StoredEvent(null, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);
            case 1:
                return new StoredEvent(EVENT_ID, null, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);
            case 2:
                return new StoredEvent(EVENT_ID, AGGREGATE_TYPE, null, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);
            case 3:
                return new StoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, null, new TestEvent(), null, null);
            case 4:
                return new StoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, null, null, null);
            default:
                throw new IllegalArgumentException("unexpected null index: " + nullIndex);
        }
    }

    /**
     * 测试聚合根标识：满足 {@link AggregateRootId} 契约。
     */static final class TestAggregateRootId implements AggregateRootId {
        private final String value;

        public TestAggregateRootId(String value) {
            this.value = value;
        }
        public String value() { return value; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestAggregateRootId)) return false;
            TestAggregateRootId other = (TestAggregateRootId) o;
            return Objects.equals(this.value, other.value);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(value);
        }
        @Override
        public String toString() {
            return "TestAggregateRootId{" + "value=" + value + "}";
        }
        private static final EntityType TYPE = new StringEntityType("TestAggregate");

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

    /**
     * 测试事件载荷。
     */
    static class TestEvent extends DomainEvent<TestAggregateRootId> {

        TestEvent() {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
        }
    }
}
