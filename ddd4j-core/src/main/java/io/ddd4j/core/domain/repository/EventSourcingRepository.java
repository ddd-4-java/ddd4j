package io.ddd4j.core.domain.repository;

import io.ddd4j.core.domain.model.AggregateRoot;

import java.io.Serializable;

/**
 * 事件溯源仓储接口（ddd4j 推荐用于 ES 场景）。
 * <p>
 *
 * <h3>与普通 {@link Repository} 的区别</h3>
 * <ul>
 *   <li><b>状态不落库</b>：聚合根的状态仅来自事件流，仓储只追加事件</li>
 *   <li><b>{@code add} 而非 save</b>：新建聚合根（add），更新事件流（update）</li>
 *   <li><b>乐观锁重试</b>：自动处理版本冲突，最多重试 3 次</li>
 *   <li><b>{@code read} 可指定版本</b>：支持从历史版本重建</li>
 * </ul>
 *
 * <h3>事件溯源工作流</h3>
 * <pre>{@code
 * // 1. 创建
 * Order order = repository.read(orderId).orElseThrow(); // 重建
 * order.place(...);                                    // 业务方法 → registerEvent
 * repository.update(order);                            // 追加事件到流
 *
 * // 2. 读取
 * Order order = repository.read(orderId);               // 从 EventStore 拉事件 → loadFromHistory
 * }</pre>
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface EventSourcingRepository<M extends AggregateRoot<ID>, ID extends Serializable> {

    /**
     * 读取聚合根最新版本。
     *
     * @param aggregateId 聚合根标识
     * @return 聚合根
     */
    M read(ID aggregateId);

    /**
     * 读取聚合根指定历史版本。
     *
     * @param aggregateId 聚合根标识
     * @param version     历史版本号
     * @return 聚合根
     */
    M read(ID aggregateId, int version);

    /**
     * 新建聚合根（追加事件流）。
     *
     * @param aggregate 聚合根（必须为新创建）
     */
    void add(M aggregate);

    /**
     * 更新聚合根（追加未提交事件到事件流）。
     *
     * @param aggregate 聚合根
     */
    void update(M aggregate);
}