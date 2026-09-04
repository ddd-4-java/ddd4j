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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

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

    private final EventPayloadSerializer serializer = new EventPayloadSerializer(sourceMapper);

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

    @Test
    void roundTripPreservesEntityIdPath() {
        // 验证 entity-id-path 也能正确回读（修复前需要 @JsonIgnoreProperties 绕过）
        OrderPlacedEvent original = new OrderPlacedEvent("thinkpad", 2);

        OrderPlacedEvent restored = (OrderPlacedEvent) serializer.deserialize(
                serializer.serialize(original), OrderPlacedEvent.class);

        // 注意：TestAggregateRootId 未注册到 EntityIdRegistry，反序列化路径走
        // StringEntityId 兜底（保留 value，type 退化为 String）—— 这是预期行为。
        assertThat(restored.getEntityIdPath().asString()).isEqualTo("String:order-1");
        assertThat(restored.getEntityIdPath().last().asString()).isEqualTo("order-1");
    }

    @Test
    void roundTripPreservesEventType() {
        // 验证 event-type 也能正确回读（修复前需要 @JsonIgnoreProperties 绕过）
        OrderPlacedEvent original = new OrderPlacedEvent("thinkpad", 2);

        OrderPlacedEvent restored = (OrderPlacedEvent) serializer.deserialize(
                serializer.serialize(original), OrderPlacedEvent.class);

        assertThat(restored.getEventType().asString()).isEqualTo("OrderPlacedEvent");
    }

    @Test
    void serializedJsonDoesNotCarryPolymorphicClassMarker() {
        // 安全收紧：序列化产物不再包含 @class 标记
        String json = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        assertThat(json).doesNotContain("\"@class\"");
    }

    @Test
    void constructionLeavesSourceMapperUntouched() throws Exception {
        // 先用 serializer 序列化（走 copy 副本路径），再验证 source mapper 未被污染
        serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        String plainJson = sourceMapper.writeValueAsString(new OrderPlacedEvent("macbook", 1));

        assertThat(plainJson).doesNotContain("@class");
    }

    @Test
    void mapperKeepsDefaultStrictUnknownPropertyHandling() {
        // Jackson 2 默认开启 FAIL_ON_UNKNOWN_PROPERTIES（修复后 entity-id-path/event-type 都能通过严格校验）
        assertThat(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    @Test
    void deserializeThrowsIllegalStateExceptionOnMalformedJson() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"productName\":", OrderPlacedEvent.class));

        assertThat(exception).hasMessage("Failed to deserialize event");
        assertThat(exception).hasCauseInstanceOf(JacksonException.class);
    }

    @Test
    void constructorRejectsNullSourceMapper() {
        assertThrows(NullPointerException.class, () -> new EventPayloadSerializer(null));
    }

    /**
     * 测试聚合根标识：满足 {@link AggregateRootId} 契约。
     */
    record TestAggregateRootId(String value) implements AggregateRootId {

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
     * 测试事件载荷（朴素样例，无任何 Jackson workaround）。
     */
    static class OrderPlacedEvent extends DomainEvent<TestAggregateRootId> {

        private String productName;
        private int quantity;

        OrderPlacedEvent() {
            // Jackson 回读
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
        }

        OrderPlacedEvent(String productName, int quantity) {
            this();
            this.productName = productName;
            this.quantity = quantity;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}