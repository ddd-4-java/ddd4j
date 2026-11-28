/*
 * Copyright ( (2024-2026 ddd4j project. All rights reserved.
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
package io.ddd4j.core.cqrs.eventstore.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link EventPayloadSerializer} 契约测试：round-trip、源 mapper 隔离、异常包装、
 * 默认严格模式下反序列化（含 entity-id-path/event-type 回读）。
 *
 * <p>注意：本版本不再使用 {@code activateDefaultTyping} 多态标记——
 * JSON 输出<b>不含</b>{@code @class}，反序列化端必须通过
 * {@link EventPayloadSerializer#deserialize(String, Class)} 显式传入目标类型。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class EventPayloadSerializerTest {

    /**
     * 测试基 mapper：Jackson 2 默认开启 FAIL_ON_UNKNOWN_PROPERTIES（与 Jackson 3 相反），
     * 此处保留默认配置并显式 findAndAddModules 注册 JavaTimeModule 以支持 java.time。
     * Jackson 2 的 JsonMapper.builder() 默认不含 JavaTimeModule（与 3 不同）。
     */
    private final ObjectMapper sourceMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    /**
     * 序列化领域事件或恢复原始对象的测试组件。
     */
    private final EventPayloadSerializer serializer = new EventPayloadSerializer(sourceMapper);

    /**
     * 验证<code>json</code> 包含 <code>"\"event-id\""</code>，<code>json</code> 包含 <code>"\"event-timestamp\""</code>，<code>json</code> 不包含 <code>"\"aggregateVersion\""</code>。
     */
    @Test
    void serializeShouldCarryBusinessAndEventMetadata() {
        String json = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));
        assertThat(json).contains("\"productName\":\"thinkpad\"");
        assertThat(json).contains("\"event-id\"");
        assertThat(json).contains("\"event-timestamp\"");
        assertThat(json).contains("\"entity-id-path\"");
        assertThat(json).doesNotContain("\"eventId\"");
        assertThat(json).doesNotContain("\"eventTimestamp\"");
        assertThat(json).doesNotContain("\"entityIdPath\"");
        assertThat(json).doesNotContain("\"aggregateVersion\"");
    }

    /**
     * 验证<code>restored</code> 的类型为 <code>OrderPlacedEvent.class</code>，<code>((OrderPlacedEvent) restored).getProductName()</code> 等于 <code>"thinkpad"</code>，<code>restored.getEventTimestamp().toInstant()</code> 等于 <code>originalTimestamp.toInstant()</code>。
     */
    @Test
    void roundTripRestoresConcreteEventWithZonedDateTimeTimestamp() {
        OrderPlacedEvent original = new OrderPlacedEvent("thinkpad", 2);
        ZonedDateTime originalTimestamp = original.getEventTimestamp();

        DomainEvent<?> restored = serializer.deserialize(serializer.serialize(original), OrderPlacedEvent.class);

        assertThat(restored).isInstanceOf(OrderPlacedEvent.class);
        assertThat(((OrderPlacedEvent) restored).getProductName()).isEqualTo("thinkpad");
        assertThat(((OrderPlacedEvent) restored).getQuantity()).isEqualTo(2);
        assertThat(restored.getEventId()).isEqualTo(original.getEventId());
        assertThat(restored.source()).isEqualTo("order-1");
        // Jackson 数字时间戳不携带 ZoneId（回读统一为 UTC），按 instant 精确到纳秒断言
        assertThat(restored.getEventTimestamp().toInstant()).isEqualTo(originalTimestamp.toInstant());
    }

    /**
     * 验证<code>restored.getEntityIdPath().asString()</code> 等于 <code>"String:order-2"</code>，<code>restored.getEntityIdPath().last().asString()</code> 等于 <code>"order-2"</code>。
     */
    @Test
    void roundTripPreservesEntityIdPath() {
        // 使用不同于无参构造器默认值的 ID，避免回读测试由构造器默认值假通过。
        OrderPlacedEvent original = new OrderPlacedEvent("thinkpad", 2, "order-2");

        OrderPlacedEvent restored = (OrderPlacedEvent) serializer.deserialize(
                serializer.serialize(original), OrderPlacedEvent.class);

        // 注意：TestAggregateRootId 未注册到 EntityIdRegistry，反序列化路径走
        // StringEntityId 兜底（保留 value，type 退化为 String）—— 这是预期行为。
        assertThat(restored.getEntityIdPath().asString()).isEqualTo("String:order-2");
        assertThat(restored.getEntityIdPath().last().asString()).isEqualTo("order-2");
    }

    /**
     * 验证<code>restored.getEventType().asString()</code> 等于 <code>"OrderPlacedEvent"</code>。
     */
    @Test
    void roundTripPreservesEventType() {
        // 验证 event-type 也能正确回读（修复前需要 @JsonIgnoreProperties 绕过）
        OrderPlacedEvent original = new OrderPlacedEvent("thinkpad", 2);

        OrderPlacedEvent restored = (OrderPlacedEvent) serializer.deserialize(
                serializer.serialize(original), OrderPlacedEvent.class);

        assertThat(restored.getEventType().asString()).isEqualTo("OrderPlacedEvent");
    }

    /**
     * 验证<code>json</code> 不包含 <code>"\"&#64;class\""</code>。
     */
    @Test
    void serializedJsonDoesNotCarryPolymorphicClassMarker() {
        // 安全收紧：序列化产物不再包含 @class 标记
        String json = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        assertThat(json).doesNotContain("\"@class\"");
    }

    /**
     * 验证<code>json</code> 包含 <code>"\"correlation-id\""</code>，<code>json</code> 不包含 <code>"correlationId"</code>。
     */
    @Test
    void causalityMetadataUsesHyphenatedPayloadFields() {
        OrderPlacedEvent cause = new OrderPlacedEvent("cause", 1);
        OrderPlacedEvent effect = new OrderPlacedEvent("effect", 2, cause);
        String json = serializer.serialize(effect);

        assertThat(json).contains("\"correlation-id\"").contains("\"causation-id\"");
        assertThat(json).doesNotContain("correlationId").doesNotContain("causationId");
    }

    /**
     * 验证<code>plainJson</code> 不包含 <code>"&#64;class"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void constructionLeavesSourceMapperUntouched() throws Exception {
        // 先用 serializer 序列化（走 copy 副本路径），再验证 source mapper 未被污染
        serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        String plainJson = sourceMapper.writeValueAsString(new OrderPlacedEvent("macbook", 1));

        assertThat(plainJson).doesNotContain("@class");
    }

    /**
     * 验证<code>sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>为真。
     */
    @Test
    void mapperKeepsDefaultStrictUnknownPropertyHandling() {
        // 源 mapper 保持严格；serializer 的内部副本允许历史事件读取未来版本新增的字段。
        assertThat(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    /**
     * 验证<code>restored.getProductName()</code> 等于 <code>"thinkpad"</code>，<code>restored.getQuantity()</code> 等于 <code>2</code>，<code>sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>为真。
     */
    @Test
    void deserializeIgnoresUnknownPayloadPropertyWithoutChangingSourceMapper() {
        String payload = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));
        String withFutureProperty = payload.substring(0, payload.length() - 1)
                + ",\"futureProperty\":\"newer-writer\"}";

        OrderPlacedEvent restored = (OrderPlacedEvent) serializer.deserialize(
                withFutureProperty, OrderPlacedEvent.class);

        assertThat(restored.getProductName()).isEqualTo("thinkpad");
        assertThat(restored.getQuantity()).isEqualTo(2);
        assertThat(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    /**
     * 验证<code>restored</code> 的类型为 <code>OrderPlacedEvent.class</code>，<code>((OrderPlacedEvent) restored).getProductName()</code> 等于 <code>"thinkpad"</code>。
     */
    @Test
    void unknownClassMarkerCannotChooseTheDeserializedType() {
        String payload = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));
        String withClassMarker = payload.substring(0, payload.length() - 1)
                + ",\"@class\":\"java.lang.ProcessBuilder\"}";

        DomainEvent<?> restored = serializer.deserialize(withClassMarker, OrderPlacedEvent.class);

        assertThat(restored).isInstanceOf(OrderPlacedEvent.class);
        assertThat(((OrderPlacedEvent) restored).getProductName()).isEqualTo("thinkpad");
    }

    /**
     * 验证<code>serializer.deserialize("{\"productName\":", OrderPlacedEvent.class)</code> 抛出 <code>IllegalStateException.class</code>，<code>exception</code> 的异常消息为 <code>"Failed to deserialize event"</code>。
     */
    @Test
    void deserializeThrowsIllegalStateExceptionOnMalformedJson() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"productName\":", OrderPlacedEvent.class));

        assertThat(exception).hasMessage("Failed to deserialize event");
        assertThat(exception).hasCauseInstanceOf(JacksonException.class);
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNullSourceMapper() {
        assertThrows(NullPointerException.class, () -> new EventPayloadSerializer(null));
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
     * 测试事件载荷（朴素样例，无任何 Jackson workaround）。
     */
    static class OrderPlacedEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 用于验证商品交易或订单快照的名称。
         */
        private String productName;
        /**
         * 测试订单或库存事件携带的商品数量。
         */
        private int quantity;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderPlacedEvent</code> 测试夹具。
         */
        OrderPlacedEvent() {
            // Jackson 回读
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
        }

        /**
         * 设置 <code>productName</code>、<code>quantity</code>，用于构造 <code>OrderPlacedEvent</code> 测试实例。
         *
         * @param productName 用于初始化 <code>OrderPlacedEvent</code> 的 <code>String</code> 值
         * @param quantity    用于初始化 <code>OrderPlacedEvent</code> 的 <code>int</code> 值
         */
        OrderPlacedEvent(String productName, int quantity) {
            this();
            this.productName = productName;
            this.quantity = quantity;
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderPlacedEvent</code> 测试夹具。
         *
         * @param productName 用于初始化 <code>OrderPlacedEvent</code> 的 <code>String</code> 值
         * @param quantity    用于初始化 <code>OrderPlacedEvent</code> 的 <code>int</code> 值
         * @param aggregateId 用于定位被测聚合的标识
         */
        OrderPlacedEvent(String productName, int quantity, String aggregateId) {
            super(new EntityIdPath(new TestAggregateRootId(aggregateId)));
            this.productName = productName;
            this.quantity = quantity;
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderPlacedEvent</code> 测试夹具。
         *
         * @param productName  用于初始化 <code>OrderPlacedEvent</code> 的 <code>String</code> 值
         * @param quantity     用于初始化 <code>OrderPlacedEvent</code> 的 <code>int</code> 值
         * @param causingEvent 用于初始化 <code>OrderPlacedEvent</code> 的 <code>Event</code> 值
         */
        OrderPlacedEvent(String productName, int quantity, Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.productName = productName;
            this.quantity = quantity;
        }

        /**
         * 读取<code>productName</code> 字段。
         *
         * @return <code>productName</code> 字段
         */
        public String getProductName() {
            return productName;
        }

        /**
         * 设置<code>productName</code> 字段。
         *
         * @param productName 要写入的值
         */
        public void setProductName(String productName) {
            this.productName = productName;
        }

        /**
         * 读取<code>quantity</code> 字段。
         *
         * @return <code>quantity</code> 字段
         */
        public int getQuantity() {
            return quantity;
        }

        /**
         * 设置<code>quantity</code> 字段。
         *
         * @param quantity 要写入的值
         */
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
