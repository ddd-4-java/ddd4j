package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link DomainEvent} Jackson 回读（round-trip）对证测试。
 *
 * <p>fixture 为<b>朴素</b>样例事件——不加 {@code @JsonIgnoreProperties}、不加任何
 * Jackson workaround；mapper 保持 Jackson 3 默认配置（{@code FAIL_ON_UNKNOWN_PROPERTIES}
 * 默认 disabled、java.time 模块内建自动注册）。2.0.x 线该组合（Jackson 2 默认严格模式）
 * 无法回读：{@code entity-id-path} 因 {@link EntityIdPath} 缺少 String creator 绑定失败
 * （丢聚合身份）；只读的 {@code event-type} 触发 {@code UnrecognizedPropertyException}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class DomainEventRoundTripTest {

    // Jackson 3 内建 java.time 支持（自动注册），朴素默认 mapper 即可完成回读
    private final JsonMapper objectMapper = new JsonMapper();

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

    @Test
    void mapperKeepsJ3DefaultLenientUnknownPropertyHandling() {
        // Jackson 3 将 FAIL_ON_UNKNOWN_PROPERTIES 默认改为 disabled（2.x 默认 enabled）；
        // 证明回读发生在 J3 默认宽松模式下（未手工启用严格模式 workaround）
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }

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

    @Test
    void valueOfIsIdempotentForSerializedForm() {
        EntityIdPath original = new EntityIdPath(new StringEntityId("order-1"), new StringEntityId("item-9"));

        assertThat(EntityIdPath.valueOf(original.asString()).asString()).isEqualTo(original.asString());
    }

    /**
     * 朴素样例事件：无任何 Jackson 注解 workaround，字段式 payload。
     */
    static final class SampleEvent extends DomainEvent<StringEntityId> {

        private String orderId;

        private long amount;

        SampleEvent() {
            // Jackson 回读
        }

        SampleEvent(String orderId, long amount) {
            super(orderId);
            this.orderId = orderId;
            this.amount = amount;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }
    }
}
