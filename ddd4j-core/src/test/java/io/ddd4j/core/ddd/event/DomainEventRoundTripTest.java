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
package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link DomainEvent} Jackson 回读（round-trip）对证测试。
 *
 * <p>fixture 为<b>朴素</b>样例事件——不加 {@code @JsonIgnoreProperties}、不加任何
 * Jackson workaround；mapper 保持默认 {@code FAIL_ON_UNKNOWN_PROPERTIES=true}。
 * 修复前该组合无法回读：{@code entity-id-path} 因 {@link EntityIdPath} 缺少
 * String creator 绑定失败（丢聚合身份）；只读的 {@code event-type} 触发
 * {@code UnrecognizedPropertyException}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class DomainEventRoundTripTest {

    // Jackson 2 不会自动注册 java.time 支持；findAndAddModules 经 SPI 发现 classpath 上的 jsr310（JavaTimeModule）
    /**
     * 用于读写 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    /**
     * 验证<code>restored.getEntityIdPath().asString()</code> 等于 <code>original.getEntityIdPath().asString()</code>，<code>restored.getEntityIdPath().last().asString()</code> 等于 <code>"order-1"</code>，<code>restored.getEventTimestamp().toInstant()</code> 等于 <code>original.getEventTimestamp().toInstant()</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void roundTripRestoresIdentityMetadataAndPayload() throws Exception {
        SampleEvent original = new SampleEvent("order-1", 2500L);
        original.setAggregateVersion(new AggregateVersion(3));

        String json = objectMapper.writeValueAsString(original);

        // 只读属性 event-type 序列化仍输出，反序列化跳过绑定（值由 getClass() 派生）
        assertThat(json).contains("\"event-type\":\"SampleEvent\"").contains("\"entity-id-path\":\"String:order-1\"");

        SampleEvent restored = objectMapper.readValue(json, SampleEvent.class);

        assertThat(restored.getEntityIdPath().asString()).isEqualTo(original.getEntityIdPath().asString());
        assertThat(restored.getEntityIdPath().last().asString()).isEqualTo("order-1");
        assertThat(restored.getOrderId()).isEqualTo("order-1");
        assertThat(restored.getAmount()).isEqualTo(2500L);
        assertThat(restored.getEventId()).isEqualTo(original.getEventId());
        assertThat(restored.getAggregateVersionInteger()).isEqualTo(3);
        assertThat(restored.getEventType().asString()).isEqualTo("SampleEvent");
        // Jackson 数字时间戳不携带 ZoneId（回读统一为 UTC），按 instant 断言
        assertThat(restored.getEventTimestamp().toInstant()).isEqualTo(original.getEventTimestamp().toInstant());
    }

    /**
     * 验证<code>objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>为真。
     */
    @Test
    void mapperKeepsPlatformDefaultUnknownPropertyHandling() {
        // 证明回读发生在默认严格模式下（未用 workaround 放宽）
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    /**
     * 验证<code>single.size()</code> 等于 <code>1</code>，<code>single.last().asString()</code> 等于 <code>"order-1"</code>，<code>multi.last().asString()</code> 等于 <code>"item-9"</code>。
     */
    @Test
    void valueOfParsesSingleAndMultiSegmentPaths() {
        EntityIdPath single = EntityIdPath.valueOf("String:order-1");
        assertThat(single.size()).isEqualTo(1);
        assertThat(single.last().asString()).isEqualTo("order-1");

        EntityIdPath multi = EntityIdPath.valueOf("String:order-1/String:item-9");
        assertThat(multi.size()).isEqualTo(2);
        assertThat(multi.first().asString()).isEqualTo("order-1");
        assertThat(multi.last().asString()).isEqualTo("item-9");
    }

    /**
     * 验证<code>EntityIdPath.valueOf("")</code> 抛出 <code>IllegalArgumentException</code>，<code>EntityIdPath.valueOf("String:")</code> 抛出 <code>IllegalArgumentException</code>。
     */
    @Test
    void valueOfRejectsMalformedPaths() {
        // 空串 / 空白
        assertThatIllegalArgumentException().isThrownBy(() -> EntityIdPath.valueOf(""));
        assertThatIllegalArgumentException().isThrownBy(() -> EntityIdPath.valueOf("   "));
        // 段内无 ':'（消息含出错段原文）
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf("order-1"))
                .withMessageContaining("order-1");
        // 空段（尾部 '/'）
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf("String:order-1/"))
                .withMessageContaining("String:order-1/");
        // 段内空 type
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf(":order-1"))
                .withMessageContaining(":order-1");
        // 段内空 value
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf("String:"))
                .withMessageContaining("String:");
    }

    /**
     * 验证<code>EntityIdPath.valueOf(original.asString()).asString()</code> 等于 <code>original.asString()</code>。
     */
    @Test
    void valueOfIsIdempotentForSerializedForm() {
        EntityIdPath original = new EntityIdPath(new StringEntityId("order-1"), new StringEntityId("item-9"));

        assertThat(EntityIdPath.valueOf(original.asString()).asString()).isEqualTo(original.asString());
    }

    /**
     * 朴素样例事件：无任何 Jackson 注解 workaround，字段式 payload。
     */
    static final class SampleEvent extends DomainEvent<StringEntityId> {

        /**
         * 订单标识。
         */
        private String orderId;

        /**
         * 测试交易使用的数量或金额。
         */
        private long amount;

        /**
         * 构造供当前测试使用的 <code>SampleEvent</code> 实例。
         */
        SampleEvent() {
            // Jackson 回读
        }

        /**
         * 设置 <code>orderId</code>、<code>amount</code>，用于构造 <code>SampleEvent</code> 测试实例。
         *
         * @param orderId 订单标识
         * @param amount  用于初始化 <code>SampleEvent</code> 的 <code>long</code> 值
         */
        SampleEvent(String orderId, long amount) {
            super(orderId);
            this.orderId = orderId;
            this.amount = amount;
        }

        /**
         * 读取订单标识。
         *
         * @return 订单标识
         */
        public String getOrderId() {
            return orderId;
        }

        /**
         * 设置订单标识。
         *
         * @param orderId 要写入的值
         */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /**
         * 读取<code>amount</code> 字段。
         *
         * @return <code>amount</code> 字段
         */
        public long getAmount() {
            return amount;
        }

        /**
         * 设置<code>amount</code> 字段。
         *
         * @param amount 要写入的值
         */
        public void setAmount(long amount) {
            this.amount = amount;
        }
    }
}
