package io.ddd4j.ddd.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.Event;


/**
 * ddd4j 领域事件基类（纯净 DDD 轨道）。
 *
 * <p>基于 ddd4j 原生 {@link DomainEvent}，提供完整的领域事件契约：
 * <ul>
 *   <li>{@code eventId} — 事件唯一标识（UUID，自动生成）</li>
 *   <li>{@code entityIdPath} — 从聚合根到事件源的路径（如 {@code "Order 1/OrderItem 2"}）</li>
 *   <li>{@code aggregateVersion} — 聚合版本号（用于乐观锁）</li>
 *   <li>{@code correlationId} / {@code causationId} — 链路追踪与因果关系</li>
 *   <li>{@code eventTimestamp} — 事件发生时间</li>
 *   <li>自动 Jackson 序列化（通过 {@code @JsonProperty} 元数据绑定）</li>
 * </ul>
 *
 * <p>与 {@code io.ddd4j.core.event}（基于 Spring ApplicationEvent）的区别：
 * <ul>
 *   <li>本类包含完整的领域事件元数据（aggregateId/version/correlationId）</li>
 *   <li>支持事件溯源（可序列化持久化到 EventStore）</li>
 *   <li>事件本身不可变（equals/hashCode 仅基于 eventId）</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * public class OrderCreatedEvent extends DddDomainEvent&lt;OrderId&gt; {
 *     public static final EventType TYPE = new EventType("OrderCreatedEvent");
 *
 *     private Money total;
 *
 *     // Jackson 反序列化用
 *     protected OrderCreatedEvent() { super(); }
 *
 *     public OrderCreatedEvent(OrderId id, Money total, AggregateVersion version) {
 *         super(new EntityIdPath(id), version);
 *         this.total = total;
 *     }
 *
 *     &#64;Override
 *     public EventType getEventType() { return TYPE; }
 *
 *     public Money getTotal() { return total; }
 * }
 * </pre>
 *
 * @param <ID> 事件源实体标识类型
 * @author wandl
 * @see DomainEvent
 * @since 3.4.x
 */
public abstract class DddDomainEvent<ID extends EntityId> extends DomainEvent<ID> {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造器（Jackson 反序列化时使用，子类必须保留）。
     */
    protected DddDomainEvent() {
        super();
    }

    /**
     * 构造领域事件。
     *
     * @param entityIdPath 从聚合根到事件源的路径
     */
    protected DddDomainEvent(EntityIdPath entityIdPath) {
        super(entityIdPath);
    }

    /**
     * 构造领域事件（带因果关联）。
     *
     * @param entityIdPath 从聚合根到事件源的路径
     * @param causationEvent 导致本事件的前置事件（用于因果关系链路追踪，可为 {@code null}）
     */
    protected DddDomainEvent(EntityIdPath entityIdPath, Event causationEvent) {
        super(entityIdPath, causationEvent);
    }

}
