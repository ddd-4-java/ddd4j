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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AsyncStoredEvent} 构造契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class AsyncStoredEventTest {

    /**
     * 验证事件身份保存和还原时使用的固定事件标识。
     */
    private static final EventId EVENT_ID = new EventId();
    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String AGGREGATE_TYPE = "Order";
    /**
     * 定位测试事件所属聚合的固定标识。
     */
    private static final AggregateRootId AGGREGATE_ID = new TestAggregateRootId("order-1");
    /**
     * 测试事件或协议记录使用的固定版本值。
     */
    private static final long VERSION = 3L;
    /**
     * 用于验证事件或投影位点往返的固定位置。
     */
    private static final long POSITION = 42L;
    /**
     * 事件或请求时间字段往返比较时使用的固定时间值。
     */
    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.now();
    /**
     * 用于校验跨层传递的测试关联标识。
     */
    private static final EventId CORRELATION_ID = new EventId();
    /**
     * 验证因果追踪字段往返时使用的固定事件标识。
     */
    private static final EventId CAUSATION_ID = new EventId();

    /**
     * 提供异步存储事件必须拒绝空值的字段及参数位置。
     *
     * @return 供参数化构造测试逐项传入的字段名和下标
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
     * 构造 <code>AsyncStoredEvent</code>、<code>TestEvent</code> 作为当前测试的输入或结果。
     *
     * @param nullIndex 本次调用使用的 <code>int</code> 参数 <code>nullIndex</code>
     * @return 根据测试条件返回的可能值，包括 <code>new AsyncStoredEvent(null, AGGREGATE_TYPE, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null)</code>、<code>new AsyncStoredEvent(EVENT_ID, null, AGGREGATE_ID, VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null)</code>
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
     * 验证<code>newAsyncStoredEventWithNullAt(nullIndex)</code> 抛出 <code>NullPointerException.class</code>。
     *
     * @param argumentName 传给 <code>assertThrows()</code> 的 <code>String</code> 输入
     * @param nullIndex    传给 <code>assertThrows()</code> 的 <code>int</code> 输入
     */
    @ParameterizedTest(name = "constructor rejects null {0}")
    @MethodSource("nullMandatoryArguments")
    void constructorRejectsNullMandatoryArgument(String argumentName, int nullIndex) {
        assertThrows(NullPointerException.class, () -> newAsyncStoredEventWithNullAt(nullIndex),
                "argument " + argumentName + " must be null-checked");
    }

    /**
     * 验证<code>storedEvent.correlationId()</code>为空，<code>storedEvent.causationId()</code>为空。
     */
    @Test
    void constructorAllowsNullCorrelationAndCausationId() {
        AsyncStoredEvent storedEvent = new AsyncStoredEvent(EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID,
                VERSION, POSITION, TIMESTAMP, new TestEvent(), null, null);

        assertNull(storedEvent.correlationId(), "correlationId should be nullable");
        assertNull(storedEvent.causationId(), "causationId should be nullable");
    }

    /**
     * 验证<code>storedEvent.eventId()</code> 与…为同一实例 <code>EVENT_ID</code>，<code>storedEvent.aggregateType()</code> 等于 <code>AGGREGATE_TYPE</code>，<code>storedEvent.causationId()</code> 与…为同一实例 <code>CAUSATION_ID</code>。
     */
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
     * 测试聚合根标识：满足 {@link AggregateRootId} 契约。
     */
    final static class TestAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("TestAggregate");
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestAggregateRootId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 测试事件载荷。
     */
    static class TestEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        TestEvent() {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
        }
    }
}
