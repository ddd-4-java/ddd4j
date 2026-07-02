package io.ddd4j.core.ddd.event;

import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;

import java.io.Serial;

/**
 * ddd4j 领域事件基类（继承 fuinorg {@link AbstractDomainEvent}，ES 轨道专用）。
 * <p>
 * 提供完整的领域事件契约：
 * <ul>
 *   <li>{@code eventId} — 事件唯一标识（UUID，自动生成）</li>
 *   <li>{@code entityIdPath} — 从聚合根到事件源的路径</li>
 *   <li>{@code aggregateVersion} — 聚合版本号（用于乐观锁）</li>
 *   <li>{@code correlationId} / {@code causationId} — 链路追踪与因果关系</li>
 *   <li>自动 Jackson 序列化</li>
 * </ul>
 *
 * <p>与 {@link DomainEvent} 的区别：
 * <ul>
 *   <li>{@link DomainEvent} — ddd4j 进程内事件（通过 DomainEventPublisher）</li>
 *   <li>{@link DddDomainEvent} — fuinorg ES 事件（可序列化持久化到 EventStore）</li>
 * </ul>
 *
 * @param <ID> 事件源实体标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class DddDomainEvent<ID extends EntityId> extends AbstractDomainEvent<ID> {

    @Serial
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
     * @param entityIdPath   从聚合根到事件源的路径
     * @param causationEvent 导致本事件的前置事件
     */
    protected DddDomainEvent(EntityIdPath entityIdPath, org.fuin.ddd4j.core.Event causationEvent) {
        super(entityIdPath, causationEvent);
    }
}
