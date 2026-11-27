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

    /** \u5e8f\u5217\u5316\u9886\u57df\u4e8b\u4ef6\u6216\u6062\u590d\u539f\u59cb\u5bf9\u8c61\u7684\u6d4b\u8bd5\u7ec4\u4ef6\u3002 */
    private final EventPayloadSerializer serializer = new EventPayloadSerializer(sourceMapper);

    /** \u9a8c\u8bc1<code>json</code> \u5305\u542b <code>"\"event-id\""</code>\uff0c<code>json</code> \u5305\u542b <code>"\"event-timestamp\""</code>\uff0c<code>json</code> \u4e0d\u5305\u542b <code>"\"aggregateVersion\""</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>restored</code> \u7684\u7c7b\u578b\u4e3a <code>OrderPlacedEvent.class</code>\uff0c<code>((OrderPlacedEvent) restored).getProductName()</code> \u7b49\u4e8e <code>"thinkpad"</code>\uff0c<code>restored.getEventTimestamp().toInstant()</code> \u7b49\u4e8e <code>originalTimestamp.toInstant()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>restored.getEntityIdPath().asString()</code> \u7b49\u4e8e <code>"String:order-2"</code>\uff0c<code>restored.getEntityIdPath().last().asString()</code> \u7b49\u4e8e <code>"order-2"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>restored.getEventType().asString()</code> \u7b49\u4e8e <code>"OrderPlacedEvent"</code>\u3002 */
    @Test
    void roundTripPreservesEventType() {
        // 验证 event-type 也能正确回读（修复前需要 @JsonIgnoreProperties 绕过）
        OrderPlacedEvent original = new OrderPlacedEvent("thinkpad", 2);

        OrderPlacedEvent restored = (OrderPlacedEvent) serializer.deserialize(
                serializer.serialize(original), OrderPlacedEvent.class);

        assertThat(restored.getEventType().asString()).isEqualTo("OrderPlacedEvent");
    }

    /** \u9a8c\u8bc1<code>json</code> \u4e0d\u5305\u542b <code>"\"&#64;class\""</code>\u3002 */
    @Test
    void serializedJsonDoesNotCarryPolymorphicClassMarker() {
        // 安全收紧：序列化产物不再包含 @class 标记
        String json = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        assertThat(json).doesNotContain("\"@class\"");
    }

    /** \u9a8c\u8bc1<code>json</code> \u5305\u542b <code>"\"correlation-id\""</code>\uff0c<code>json</code> \u4e0d\u5305\u542b <code>"correlationId"</code>\u3002 */
    @Test
    void causalityMetadataUsesHyphenatedPayloadFields() {
        OrderPlacedEvent cause = new OrderPlacedEvent("cause", 1);
        OrderPlacedEvent effect = new OrderPlacedEvent("effect", 2, cause);
        String json = serializer.serialize(effect);

        assertThat(json).contains("\"correlation-id\"").contains("\"causation-id\"");
        assertThat(json).doesNotContain("correlationId").doesNotContain("causationId");
    }

    /**
     * \u9a8c\u8bc1<code>plainJson</code> \u4e0d\u5305\u542b <code>"&#64;class"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void constructionLeavesSourceMapperUntouched() throws Exception {
        // 先用 serializer 序列化（走 copy 副本路径），再验证 source mapper 未被污染
        serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        String plainJson = sourceMapper.writeValueAsString(new OrderPlacedEvent("macbook", 1));

        assertThat(plainJson).doesNotContain("@class");
    }

    /** \u9a8c\u8bc1<code>sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>\u4e3a\u771f\u3002 */
    @Test
    void mapperKeepsDefaultStrictUnknownPropertyHandling() {
        // 源 mapper 保持严格；serializer 的内部副本允许历史事件读取未来版本新增的字段。
        assertThat(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    /** \u9a8c\u8bc1<code>restored.getProductName()</code> \u7b49\u4e8e <code>"thinkpad"</code>\uff0c<code>restored.getQuantity()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>restored</code> \u7684\u7c7b\u578b\u4e3a <code>OrderPlacedEvent.class</code>\uff0c<code>((OrderPlacedEvent) restored).getProductName()</code> \u7b49\u4e8e <code>"thinkpad"</code>\u3002 */
    @Test
    void unknownClassMarkerCannotChooseTheDeserializedType() {
        String payload = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));
        String withClassMarker = payload.substring(0, payload.length() - 1)
                + ",\"@class\":\"java.lang.ProcessBuilder\"}";

        DomainEvent<?> restored = serializer.deserialize(withClassMarker, OrderPlacedEvent.class);

        assertThat(restored).isInstanceOf(OrderPlacedEvent.class);
        assertThat(((OrderPlacedEvent) restored).getProductName()).isEqualTo("thinkpad");
    }

    /** \u9a8c\u8bc1<code>serializer.deserialize("{\"productName\":", OrderPlacedEvent.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception</code> \u7684\u5f02\u5e38\u6d88\u606f\u4e3a <code>"Failed to deserialize event"</code>\u3002 */
    @Test
    void deserializeThrowsIllegalStateExceptionOnMalformedJson() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"productName\":", OrderPlacedEvent.class));

        assertThat(exception).hasMessage("Failed to deserialize event");
        assertThat(exception).hasCauseInstanceOf(JacksonException.class);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullSourceMapper() {
        assertThrows(NullPointerException.class, () -> new EventPayloadSerializer(null));
    }

    /**
     * 测试聚合根标识：满足 {@link AggregateRootId} 契约。
     */
    final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("TestAggregate");
        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
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

        /** \u7528\u4e8e\u9a8c\u8bc1\u5546\u54c1\u4ea4\u6613\u6216\u8ba2\u5355\u5feb\u7167\u7684\u540d\u79f0\u3002 */
        private String productName;
        /** \u6d4b\u8bd5\u8ba2\u5355\u6216\u5e93\u5b58\u4e8b\u4ef6\u643a\u5e26\u7684\u5546\u54c1\u6570\u91cf\u3002 */
        private int quantity;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderPlacedEvent() {
            // Jackson 回读
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
        }

        /**
         * \u8bbe\u7f6e <code>productName</code>\u3001<code>quantity</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>OrderPlacedEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param productName \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>String</code> \u503c
         * @param quantity \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>int</code> \u503c
         */
        OrderPlacedEvent(String productName, int quantity) {
            this();
            this.productName = productName;
            this.quantity = quantity;
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param productName \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>String</code> \u503c
         * @param quantity \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>int</code> \u503c
         * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
         */
        OrderPlacedEvent(String productName, int quantity, String aggregateId) {
            super(new EntityIdPath(new TestAggregateRootId(aggregateId)));
            this.productName = productName;
            this.quantity = quantity;
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param productName \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>String</code> \u503c
         * @param quantity \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>int</code> \u503c
         * @param causingEvent \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPlacedEvent</code> \u7684 <code>Event</code> \u503c
         */
        OrderPlacedEvent(String productName, int quantity, Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.productName = productName;
            this.quantity = quantity;
        }

        /**
         * \u8bfb\u53d6<code>productName</code> \u5b57\u6bb5\u3002
         * @return <code>productName</code> \u5b57\u6bb5
         */
        public String getProductName() {
            return productName;
        }

        /**
         * \u8bbe\u7f6e<code>productName</code> \u5b57\u6bb5\u3002
         * @param productName \u8981\u5199\u5165\u7684\u503c
         */
        public void setProductName(String productName) {
            this.productName = productName;
        }

        /**
         * \u8bfb\u53d6<code>quantity</code> \u5b57\u6bb5\u3002
         * @return <code>quantity</code> \u5b57\u6bb5
         */
        public int getQuantity() {
            return quantity;
        }

        /**
         * \u8bbe\u7f6e<code>quantity</code> \u5b57\u6bb5\u3002
         * @param quantity \u8981\u5199\u5165\u7684\u503c
         */
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
