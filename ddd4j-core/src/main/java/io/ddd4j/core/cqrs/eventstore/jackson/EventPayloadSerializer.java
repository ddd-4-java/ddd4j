package io.ddd4j.core.cqrs.eventstore.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.Objects;

/**
 * 领域事件 payload Jackson 2 序列化器（JDK8 轨道）。
 *
 * <h3>类型安全策略</h3>
 * <p>不使用 Jackson {@code activateDefaultTyping} / {@code @class} 多态标记——
 * 彻底关闭「{@code @class} 指向任意类」的多态反序列化攻击面。
 * 反序列化端始终由调用方显式传入目标 {@link DomainEvent} 子类型
 * （{@link #deserialize(String, Class)}），事件类名由存储层的
 * {@code event_type} 列提供。
 *
 * <h3>基类派生字段与存储列的分工</h3>
 * <p>{@link DomainEvent} 基类的派生 getter（eventId / eventType /
 * eventTimestamp / correlationId / causationId / entityIdPath / aggregateVersion）
 * 通过 Jackson mixin 全部忽略：这些元数据由事件存储的独立列承载并作为
 * 读回的权威来源（StoredEvent 重建时取列值），payload JSON 只承载具体事件
 * 子类的业务属性。子类需遵循 JavaBean 约定（无参构造 + getter/setter）。
 *
 * <h3>mapper 隔离</h3>
 * <p>构造器对传入 mapper 先执行 {@link ObjectMapper#copy()}，配置只作用于副本，
 * 调用方的全局 mapper（如 Spring 全局 ObjectMapper）不受影响。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public final class EventPayloadSerializer {

    /** 忽略 {@link DomainEvent} 基类派生 getter 的 mixin——元数据以存储列为准。 */
    private abstract static class DomainEventMixin {
        @JsonIgnore abstract io.ddd4j.core.ddd.event.EventId getEventId();
        @JsonIgnore abstract io.ddd4j.core.ddd.event.EventType getEventType();
        @JsonIgnore abstract java.time.ZonedDateTime getEventTimestamp();
        @JsonIgnore abstract io.ddd4j.core.ddd.event.EventId getCorrelationId();
        @JsonIgnore abstract io.ddd4j.core.ddd.event.EventId getCausationId();
        @JsonIgnore abstract io.ddd4j.core.ddd.event.EntityIdPath getEntityIdPath();
        @JsonIgnore abstract io.ddd4j.core.ddd.event.AggregateVersion getAggregateVersion();
    }

    private final ObjectMapper objectMapper;

    public EventPayloadSerializer(ObjectMapper source) {
        ObjectMapper mapper = Objects.requireNonNull(source, "source must not be null").copy();
        mapper.addMixIn(DomainEvent.class, DomainEventMixin.class);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        this.objectMapper = mapper;
    }

    /** 使用默认配置创建序列化器。 */
    public EventPayloadSerializer() {
        this(new ObjectMapper());
    }

    /**
     * 序列化领域事件。
     *
     * @param event 领域事件
     * @return 不含多态标记的 JSON 文本（仅业务属性）
     * @throws IllegalStateException 序列化失败
     */
    public String serialize(DomainEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    /**
     * 反序列化领域事件。
     *
     * <p>目标类型由调用方显式传入（来自存储层 {@code event_type} 列），
     * 不依赖 JSON 内的类型标记。
     *
     * @param json      JSON 文本
     * @param eventType 目标事件类型
     * @return 还原的领域事件
     * @throws IllegalStateException 反序列化失败
     */
    public DomainEvent<?> deserialize(String json, Class<? extends DomainEvent<?>> eventType) {
        try {
            return objectMapper.readValue(json, eventType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize event", e);
        }
    }
}
