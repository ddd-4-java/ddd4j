package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;

/** 将现有订单字符串标识接入 EventStore 流接口，不改变订单领域对象。 */
public final class OrderStreamId implements AggregateRootId {
    /** 既有订单 ID 的文本值。 */
    private final String value;
    /**
     * @param value 既有订单标识
     */
    public OrderStreamId(String value) { this.value = value; }
    @Override public EntityType getType() { return new StringEntityType("Order"); }
    @Override public String asString() { return value; }
    @Override public String asTypedString() { return "Order:" + value; }
}
