package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityId;

/** 持久化 Outbox 传输信封；保留原消息标识、事件类型及业务载荷，供幂等中继使用。 */
public final class OrderOutboxEvent extends DomainEvent<StringEntityId> {
    /** 原 Outbox 消息标识，用于持久幂等去重。 */
    private final String outboxId;
    /** 既有订单标识。 */
    private final String orderId;
    /** 原共享订单事件的类型名。 */
    private final String originalType;
    /** 原事件业务载荷。 */
    private final JsonNode originalPayload;
    /**
     * @param outboxId 原消息 ID
     * @param orderId 原订单 ID
     * @param originalType 原事件类型
     * @param originalPayload 原始业务载荷
     */
    @JsonCreator
    public OrderOutboxEvent(@JsonProperty("outboxId") String outboxId, @JsonProperty("orderId") String orderId,
            @JsonProperty("originalType") String originalType, @JsonProperty("originalPayload") JsonNode originalPayload) {
        super(orderId);
        this.outboxId = outboxId;
        this.orderId = orderId;
        this.originalType = originalType;
        this.originalPayload = originalPayload;
    }
    @Override public EventId getEventId() { return EventId.valueOf(outboxId); }
    /**
     * @return 原 Outbox 标识
     */
    @JsonProperty public String getOutboxId() { return outboxId; }
    /**
     * @return 原订单标识
     */
    @JsonProperty public String getOrderId() { return orderId; }
    /**
     * @return 原事件类型
     */
    @JsonProperty public String getOriginalType() { return originalType; }
    /**
     * @return 原业务载荷
     */
    @JsonProperty public JsonNode getOriginalPayload() { return originalPayload; }
}
