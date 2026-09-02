package io.ddd4j.mq.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.contract.MQEvent;
import lombok.Getter;

/**
 * 领域事件 MQ 载体（回填自 3.0.x c36ad164）。
 *
 * <p>携带领域事件完全限定类名与 JSON 序列化结果，
 * 消费端据 {@code domainEventType} 选择反序列化目标类型
 * （建议配合 {@code EventDeserializer} 白名单校验后还原）。
 */
@Getter
public class DomainEventCarrier extends MQEvent {

    private static final long serialVersionUID = 1L;

    /** 领域事件完全限定类名，消费端据此选择反序列化目标类型。 */
    private final String domainEventType;

    /** 领域事件 JSON 序列化结果。 */
    private final String payload;

    /**
     * 构造领域事件载体。
     *
     * @param domainEventType 领域事件完全限定类名
     * @param payload         领域事件 JSON 序列化结果
     */
    @JsonCreator
    public DomainEventCarrier(@JsonProperty("domainEventType") String domainEventType,
                              @JsonProperty("payload") String payload) {
        this.domainEventType = domainEventType;
        this.payload = payload;
    }
}
