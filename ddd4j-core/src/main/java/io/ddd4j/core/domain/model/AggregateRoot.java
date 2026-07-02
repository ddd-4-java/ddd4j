package io.ddd4j.core.domain.model;

import io.ddd4j.core.domain.event.DomainEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 统一的领域聚合根基类（ddd4j 唯一推荐）。
 * <p>
 * 取代以下三条历史轨道：
 * <ul>
 *   <li>{@code io.ddd4j.core.domain.contract.Model}（ActiveRecord 风格，耦合 ORM）</li>
 *   <li>{@code io.ddd4j.core.domain.model.AggregateRoot}（轻量版，POJO 持久化）</li>
 *   <li>{@code io.ddd4j.core.ddd.aggregate.DddAggregateRoot}（fuinorg ES 风格）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>零基础设施依赖</b>：不继承任何 ORM / 事件存储框架类，可由 MyBatis / JPA / JDBI / Panache 任一适配层持久化</li>
 *   <li><b>状态变更走事件</b>：业务方法通过 {@link #registerEvent} 产生事件，事件订阅方负责修改状态</li>
 *   <li><b>纯充血模型</b>：业务逻辑封装在聚合根内部，避免贫血的服务层</li>
 * </ul>
 *
 * <h3>三种持久化策略</h3>
 * <table border="1">
 *   <tr><th>场景</th><th>持久化方式</th><th>扩展点</th></tr>
 *   <tr><td>简单 CRUD</td><td>MyBatis-Plus / JPA 仓库映射</td><td>直接继承本类</td></tr>
 *   <tr><td>事件溯源</td><td>继承 {@link io.ddd4j.core.domain.repository.EventSourcingRepository}</td><td>添加审计字段</td></tr>
 *   <tr><td>读模型投影</td><td>JPA 实体 + 投影事件处理器</td><td>独立于本类</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId> {
 *     private OrderId id;
 *     private Money total;
 *     private OrderStatus status;
 *
 *     public Order(OrderId id, Money total) {
 *         super();
 *         registerEvent(new OrderCreatedEvent(id, total));
 *     }
 *
 *     public void pay(Money amount) {
 *         if (status != OrderStatus.PENDING) {
 *             throw new IllegalStateException("Order is not pending");
 *         }
 *         registerEvent(new OrderPaidEvent(id, amount));
 *     }
 * }
 * }</pre>
 *
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public abstract class AggregateRoot<ID extends Serializable> implements Entity<ID> {

    private static final long serialVersionUID = 1L;

    private transient List<DomainEvent<?>> domainEvents = new ArrayList<>();

    /**
     * 注册聚合根产生的领域事件。
     * <p>
     * 业务方法应通过此方法产生事件，而非直接修改字段。
     * 事件订阅方（{@code @ApplyEvent} 方法或仓储实现）负责实际修改状态。
     *
     * @param event 领域事件
     */
    protected void registerEvent(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        mutableDomainEvents().add(event);
    }

    /**
     * 返回未提交的领域事件（不可变视图）。
     *
     * @return 不可变的事件列表
     */
    public List<DomainEvent<?>> domainEvents() {
        return Collections.unmodifiableList(mutableDomainEvents());
    }

    /**
     * 返回并清空未提交的领域事件。
     * <p>
     * 由仓储实现或事务包装器在持久化成功后调用。
     *
     * @return 不可变的事件快照
     */
    public List<DomainEvent<?>> pullDomainEvents() {
        List<DomainEvent<?>> events = List.copyOf(mutableDomainEvents());
        clearDomainEvents();
        return events;
    }

    /**
     * 清空未提交的领域事件。
     */
    public void clearDomainEvents() {
        mutableDomainEvents().clear();
    }

    /**
     * 是否存在未提交的领域事件。
     *
     * @return {@code true} 表示有待持久化或发布的事件
     */
    public boolean hasDomainEvents() {
        return !mutableDomainEvents().isEmpty();
    }

    private List<DomainEvent<?>> mutableDomainEvents() {
        if (Objects.isNull(domainEvents)) {
            domainEvents = new ArrayList<>();
        }
        return domainEvents;
    }
}