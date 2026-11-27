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
    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    /**
     * \u9a8c\u8bc1<code>restored.getEntityIdPath().asString()</code> \u7b49\u4e8e <code>original.getEntityIdPath().asString()</code>\uff0c<code>restored.getEntityIdPath().last().asString()</code> \u7b49\u4e8e <code>"order-1"</code>\uff0c<code>restored.getEventTimestamp().toInstant()</code> \u7b49\u4e8e <code>original.getEventTimestamp().toInstant()</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>\u4e3a\u771f\u3002 */
    @Test
    void mapperKeepsPlatformDefaultUnknownPropertyHandling() {
        // 证明回读发生在默认严格模式下（未用 workaround 放宽）
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    /** \u9a8c\u8bc1<code>single.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>single.last().asString()</code> \u7b49\u4e8e <code>"order-1"</code>\uff0c<code>multi.last().asString()</code> \u7b49\u4e8e <code>"item-9"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>EntityIdPath.valueOf("")</code> \u629b\u51fa <code>IllegalArgumentException</code>\uff0c<code>EntityIdPath.valueOf("String:")</code> \u629b\u51fa <code>IllegalArgumentException</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>EntityIdPath.valueOf(original.asString()).asString()</code> \u7b49\u4e8e <code>original.asString()</code>\u3002 */
    @Test
    void valueOfIsIdempotentForSerializedForm() {
        EntityIdPath original = new EntityIdPath(new StringEntityId("order-1"), new StringEntityId("item-9"));

        assertThat(EntityIdPath.valueOf(original.asString()).asString()).isEqualTo(original.asString());
    }

    /**
     * 朴素样例事件：无任何 Jackson 注解 workaround，字段式 payload。
     */
    static final class SampleEvent extends DomainEvent<StringEntityId> {

        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        private String orderId;

        /** \u6d4b\u8bd5\u4ea4\u6613\u4f7f\u7528\u7684\u6570\u91cf\u6216\u91d1\u989d\u3002 */
        private long amount;

        /** \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>SampleEvent</code> \u5b9e\u4f8b\u3002 */
        SampleEvent() {
            // Jackson 回读
        }

        /**
         * \u8bbe\u7f6e <code>orderId</code>\u3001<code>amount</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>SampleEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         * @param amount \u7528\u4e8e\u521d\u59cb\u5316 <code>SampleEvent</code> \u7684 <code>long</code> \u503c
         */
        SampleEvent(String orderId, long amount) {
            super(orderId);
            this.orderId = orderId;
            this.amount = amount;
        }

        /**
         * \u8bfb\u53d6\u8ba2\u5355\u6807\u8bc6\u3002
         * @return \u8ba2\u5355\u6807\u8bc6
         */
        public String getOrderId() {
            return orderId;
        }

        /**
         * \u8bbe\u7f6e\u8ba2\u5355\u6807\u8bc6\u3002
         * @param orderId \u8981\u5199\u5165\u7684\u503c
         */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /**
         * \u8bfb\u53d6<code>amount</code> \u5b57\u6bb5\u3002
         * @return <code>amount</code> \u5b57\u6bb5
         */
        public long getAmount() {
            return amount;
        }

        /**
         * \u8bbe\u7f6e<code>amount</code> \u5b57\u6bb5\u3002
         * @param amount \u8981\u5199\u5165\u7684\u503c
         */
        public void setAmount(long amount) {
            this.amount = amount;
        }
    }
}
