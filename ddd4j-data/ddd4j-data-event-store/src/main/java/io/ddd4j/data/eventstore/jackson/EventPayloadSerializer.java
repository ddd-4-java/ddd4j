package io.ddd4j.data.eventstore.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.Objects;

/**
 * 领域事件 payload Jackson 序列化器（ADR-0005）。
 *
 * <p>使用 Jackson 默认类型信息（{@code @class} 属性，{@link JsonTypeInfo.As#PROPERTY}）
 * 支持多态反序列化：序列化时在 JSON 根部写入具体事件类名，反序列化时据此还原
 * {@link DomainEvent} 子类型。类型校验通过 {@link BasicPolymorphicTypeValidator}
 * 限定在 {@code DomainEvent} 基类型内，收窄 {@code activateDefaultTyping} 的多态攻击面。
 *
 * <h3>跨运行时共享</h3>
 * <p>Spring / Quarkus / Micronaut / Helidon / Javalin / Vert.x / Dropwizard 均使用
 * Jackson 2.22.x，本抽象不绑定任何运行时。
 *
 * <h3>mapper 隔离</h3>
 * <p>构造器对传入 mapper 先执行 {@link ObjectMapper#copy()}，再<b>在副本上</b>启用
 * default typing——构造后传入的 source mapper 保持不受影响（如 Spring 全局 mapper
 * 不会被子类型标记污染）。{@code copy()} 会携带 source 已注册的全部 module
 * （如 jsr310 {@code JavaTimeModule}）与配置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class EventPayloadSerializer {

    private final ObjectMapper objectMapper;

    /**
     * 创建序列化器。
     *
     * @param source 源 mapper；构造后保持不变，多态 typing 只配置在其副本上
     */
    public EventPayloadSerializer(ObjectMapper source) {
        this.objectMapper = Objects.requireNonNull(source, "source must not be null").copy();
        this.objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(DomainEvent.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
    }

    /**
     * 序列化领域事件。
     *
     * @param event 领域事件
     * @return 含 {@code @class} 多态标记的 JSON 文本
     * @throws IllegalStateException 序列化失败
     */
    public String serialize(DomainEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    /**
     * 反序列化领域事件。
     *
     * @param json      含 {@code @class} 多态标记的 JSON 文本
     * @param eventType 目标事件类型
     * @return 还原的领域事件
     * @throws IllegalStateException 反序列化失败
     */
    public DomainEvent<?> deserialize(String json, Class<? extends DomainEvent<?>> eventType) {
        try {
            return objectMapper.readValue(json, eventType);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize event", e);
        }
    }
}
