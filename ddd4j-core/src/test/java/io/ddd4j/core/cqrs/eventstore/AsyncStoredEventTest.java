/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.eventstore;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AsyncStoredEvent} 构造契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class AsyncStoredEventTest {

    /** \u9a8c\u8bc1\u4e8b\u4ef6\u8eab\u4efd\u4fdd\u5b58\u548c\u8fd8\u539f\u65f6\u4f7f\u7528\u7684\u56fa\u5b9a\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    private static final EventId EVENT_ID = new EventId();
    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String AGGREGATE_TYPE = "Order";
    /** \u5b9a\u4f4d\u6d4b\u8bd5\u4e8b\u4ef6\u6240\u5c5e\u805a\u5408\u7684\u56fa\u5b9a\u6807\u8bc6\u3002 */
    private static final AggregateRootId AGGREGATE_ID = new TestAggregateRootId("order-1");
    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6216\u534f\u8bae\u8bb0\u5f55\u4f7f\u7528\u7684\u56fa\u5b9a\u7248\u672c\u503c\u3002 */
    private static final long VERSION = 3L;
    /** \u7528\u4e8e\u9a8c\u8bc1\u4e8b\u4ef6\u6216\u6295\u5f71\u4f4d\u70b9\u5f80\u8fd4\u7684\u56fa\u5b9a\u4f4d\u7f6e\u3002 */
    private static final long POSITION = 42L;
    /** \u4e8b\u4ef6\u6216\u8bf7\u6c42\u65f6\u95f4\u5b57\u6bb5\u5f80\u8fd4\u6bd4\u8f83\u65f6\u4f7f\u7528\u7684\u56fa\u5b9a\u65f6\u95f4\u503c\u3002 */
    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.now();
    /** \u7528\u4e8e\u6821\u9a8c\u8de8\u5c42\u4f20\u9012\u7684\u6d4b\u8bd5\u5173\u8054\u6807\u8bc6\u3002 */
    private static final EventId CORRELATION_ID = new EventId();
    /** \u9a8c\u8bc1\u56e0\u679c\u8ffd\u8e2a\u5b57\u6bb5\u5f80\u8fd4\u65f6\u4f7f\u7528\u7684\u56fa\u5b9a\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    private static final EventId CAUSATION_ID = new EventId();

    /**
     * \u63d0\u4f9b\u5f02\u6b65\u5b58\u50a8\u4e8b\u4ef6\u5fc5\u987b\u62d2\u7edd\u7a7a\u503c\u7684\u5b57\u6bb5\u53ca\u53c2\u6570\u4f4d\u7f6e\u3002
     * @return \u4f9b\u53c2\u6570\u5316\u6784\u9020\u6d4b\u8bd5\u9010\u9879\u4f20\u5165\u7684\u5b57\u6bb5\u540d\u548c\u4e0b\u6807
     */
    static Stream<Arguments> nullMandatoryArguments() {
        return Stream.of(
                Arguments.of("eventId", 0),
                Arguments.of("aggregateType", 1),
                Arguments.of("aggregateId", 2),
                Arguments.of("timestamp", 3),
                Arguments.of("payload", 4));
    }

    /**
     * \u9a8c\u8bc1<code>newAsyncStoredEventWithNullAt(nullIndex)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002
     * @param argumentName \u4f20\u7ed9 <code>assertThrows()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param nullIndex \u4f20\u7ed9 <code>assertThrows()</code> \u7684 <code>int</code> \u8f93\u5165
     */
    @ParameterizedTest(name = "constructor rejects null {0}")
    @MethodSource("nullMandatoryArguments")
    void constructorRejectsNullMandatoryArgument(String argumentName, int nullIndex) {
        assertThrows(NullPointerException.class, () -> newAsyncStoredEventWithNullAt(nullIndex),
                "argument " + argumentName + " must be null-checked");
    }

    /** \u9a8c\u8bc1<code>storedEvent.correlationId()</code>\u4e3a\u7a7a\uff0c<code>storedEvent.causationId()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void constructorAllowsNullCorrelationAndCausationId() {
        AsyncStoredEvent storedEvent = new AsyncStoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID,
                VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);

        assertNull(storedEvent.correlationId(), "correlationId should be nullable");
        assertNull(storedEvent.causationId(), "causationId should be nullable");
    }

    /** \u9a8c\u8bc1<code>storedEvent.eventId()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>EVENT_ID</code>\uff0c<code>storedEvent.aggregateType()</code> \u7b49\u4e8e <code>AGGREGATE_TYPE</code>\uff0c<code>storedEvent.causationId()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>CAUSATION_ID</code>\u3002 */
    @Test
    void accessorsReturnConstructorValues() {
        DomainEvent<?> payload = new TestEvent();

        AsyncStoredEvent storedEvent = new AsyncStoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID,
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

    /**
     * \u6784\u9020 <code>AsyncStoredEvent</code>\u3001<code>TestEvent</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param nullIndex \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>int</code> \u53c2\u6570 <code>nullIndex</code>
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>new AsyncStoredEvent(null, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null)</code>\u3001<code>new AsyncStoredEvent(EVENT_ID, null, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null)</code>
     */
    private static AsyncStoredEvent newAsyncStoredEventWithNullAt(int nullIndex) {
        switch (nullIndex) {
            case 0:
                return new AsyncStoredEvent(null, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);
            case 1:
                return new AsyncStoredEvent(EVENT_ID, null, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);
            case 2:
                return new AsyncStoredEvent(EVENT_ID, AGGREGATE_TYPE, null, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);
            case 3:
                return new AsyncStoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, null, new TestEvent(), null, null);
            case 4:
                return new AsyncStoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, null, null, null);
            default:
                throw new IllegalArgumentException("unexpected null index: " + nullIndex);
        }
    }

    /**
     * 测试聚合根标识：满足 {@link AggregateRootId} 契约。
     */
    final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("TestAggregate");

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 测试事件载荷。
     */
    static class TestEvent extends DomainEvent<TestAggregateRootId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEvent() {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
        }
    }
}
