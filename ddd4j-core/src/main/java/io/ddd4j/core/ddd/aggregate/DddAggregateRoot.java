package io.ddd4j.core.ddd.aggregate;

import lombok.Getter;
import org.fuin.ddd4j.core.AbstractAggregateRoot;
import org.fuin.ddd4j.core.AggregateRootId;

import java.time.LocalDateTime;

/**
 * ddd4j 聚合根基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link AbstractAggregateRoot}，提供：
 * <ul>
 *   <li>领域事件收集（{@code apply} 产生事件 → {@code getUncommittedChanges} 取出）</li>
 *   <li>事件溯源状态重建（{@code loadFromHistory} 重放历史事件）</li>
 *   <li>{@code @ApplyEvent} 注解驱动的事件处理方法</li>
 *   <li>审计字段（{@code createTime}/{@code updateTime}，无 ORM 注解）</li>
 * </ul>
 *
 * <p>与 {@code io.ddd4j.core.entity.BaseEntity}（MyBatis Plus ActiveRecord 轨道）的区别：
 * <ul>
 *   <li>本类 <b>不继承</b> 任何 ORM 框架类，领域层零基础设施依赖</li>
 *   <li>状态变更只能通过 {@code apply(event)}，没有直接 setter 后门</li>
 *   <li>支持事件溯源（Event Sourcing），可从事件流重建状态</li>
 * </ul>
 *
 * @param <ID> 聚合根标识类型（必须是 {@link AggregateRootId} 子类型）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see AbstractAggregateRoot
 * @see org.fuin.ddd4j.core.ApplyEvent
 * @since 3.4.x
 */
@Getter
public abstract class DddAggregateRoot<ID extends AggregateRootId> extends AbstractAggregateRoot<ID> {

    /**
     * 创建时间（审计字段，无 ORM 注解）
     */
    protected LocalDateTime createTime;

    /**
     * 更新时间（审计字段，无 ORM 注解）
     */
    protected LocalDateTime updateTime;

    /**
     * 默认构造器（事件回放时使用，子类必须保留无参构造）。
     */
    protected DddAggregateRoot() {
        super();
    }

}
