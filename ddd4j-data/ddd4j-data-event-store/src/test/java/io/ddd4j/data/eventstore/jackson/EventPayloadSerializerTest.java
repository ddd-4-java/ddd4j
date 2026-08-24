package io.ddd4j.data.eventstore.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * {@link EventPayloadSerializer} 契约测试：多态标记（{@code @class}）、
 * round-trip（含 {@link ZonedDateTime} 时间戳）、源 mapper 隔离与异常包装。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class EventPayloadSerializerTest {

    private static final String CLASS_MARKER = "\"@class\":\"" + OrderPlacedEvent.class.getName() + "\"";

    /**
     * 测试基 mapper：注册 JavaTimeModule 支持 java.time（serializer 构造时 copy() 会携带该 module）。
     */
    private final ObjectMapper sourceMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
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
    void serializedJsonCarriesPolymorphicClassMarker() {
        String json = serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        assertThat(json).contains(CLASS_MARKER);
    }

    @Test
    void constructionLeavesSourceMapperUntouched() throws Exception {
        // 先用 serializer 序列化（触发 typing 路径），再验证 source mapper 未被污染
        serializer.serialize(new OrderPlacedEvent("thinkpad", 2));

        String plainJson = sourceMapper.writeValueAsString(new OrderPlacedEvent("macbook", 1));

        assertThat(plainJson).doesNotContain("@class");
    }

    @Test
    void deserializeThrowsIllegalStateExceptionOnMalformedJson() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"@class\":\"" + OrderPlacedEvent.class.getName() + "\",\"productName\":",
                        OrderPlacedEvent.class));

        assertThat(exception).hasMessage("Failed to deserialize event");
        assertThat(exception).hasCauseInstanceOf(JacksonException.class);
    }

    @Test
    void constructorRejectsNullSourceMapper() {
        assertThrows(NullPointerException.class, () -> new EventPayloadSerializer(null));
    }

    /**
     * 测试聚合根标识：满足 {@link AggregateRootId} 契约（照 StoredEventTest fixture 模式）。
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
     * 测试事件载荷。
     *
     * <p>{@code @JsonIgnoreProperties} 说明（fixture 层妥协，非 serializer 契约）：ddd4j-core 的
     * {@code EntityIdPath} 目前只有 {@code @JsonValue} 序列化而无 String 反序列化 creator，
     * 只读属性 {@code event-type} 也无法回读，二者在 Jackson 默认严格模式下阻断整个事件的
     * 反序列化。本测试聚焦 {@link EventPayloadSerializer} 的多态契约，故忽略这两个元数据属性；
     * {@code entityIdPath} 由无参构造器重建为同值（source() 断言仍有效）。
     * 完整元数据回读需 ddd4j-core 补充 creator（见任务报告 follow-up）。
     */
    @JsonIgnoreProperties({"entity-id-path", "event-type"})
    static class OrderPlacedEvent extends DomainEvent<TestAggregateRootId> {

        private String productName;
        private int quantity;

        OrderPlacedEvent() {
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
