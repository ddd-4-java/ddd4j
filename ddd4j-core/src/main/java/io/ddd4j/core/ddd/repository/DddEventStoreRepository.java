package io.ddd4j.core.ddd.repository;

import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.esc.EventStoreRepository;
import org.fuin.esc.api.EventStore;

import io.ddd4j.core.ddd.aggregate.DddAggregateRoot;

/**
 * ddd4j 事件溯源仓储基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link EventStoreRepository}，封装事件溯源的底层细节：
 * <ul>
 *   <li>{@code read(id, version)} — 从 EventStore 读取事件流 → {@code loadFromHistory} 重建聚合根</li>
 *   <li>{@code update(aggregate)} — 取出 {@code getUncommittedChanges()} → 追加到事件流</li>
 *   <li>{@code add(aggregate)} — 创建新聚合根的事件流</li>
 *   <li>乐观锁冲突重试（最多 3 次）</li>
 * </ul>
 *
 * <p>状态不落库，只追加事件流——这是事件溯源的核心。读侧通过 CQRS 投影（{@code JpaView}）独立查询。
 *
 * <p>使用方式（参考 ddd-cqrs-4-java-example 的 EventStorePersonRepository）：
 * <pre>
 * &#64;Repository
 * public class OrderEventStoreRepository
 *         extends DddEventStoreRepository&lt;OrderId, Order&gt;
 *         implements OrderRepository {
 *
 *     public OrderEventStoreRepository(EventStore eventStore) {
 *         super(eventStore);
 *     }
 *
 *     &#64;Override
 *     public Class&lt;Order&gt; getAggregateClass() { return Order.class; }
 *
 *     &#64;Override
 *     public EntityType getAggregateType() { return OrderId.TYPE; }
 *
 *     &#64;Override
 *     public Order create() { return new Order(); } // 事件回放用空实例
 *
 *     &#64;Override
 *     public String getIdParamName() { return "orderId"; }
 * }
 * </pre>
 *
 * @param <ID> 聚合根标识类型
 * @param <A> 聚合根类型（必须是 {@link DddAggregateRoot} 子类）
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @see EventStoreRepository
 * @since 3.4.x
 */
public abstract class DddEventStoreRepository<ID extends AggregateRootId, A extends DddAggregateRoot<ID>> extends EventStoreRepository<ID, A> {

    /**
     * 构造事件溯源仓储。
     *
     * @param eventStore 事件存储（如 esc-mem 内存版，或 KurrentDB 生产版）
     */
    protected DddEventStoreRepository(EventStore eventStore) {
        super(eventStore);
    }

}
