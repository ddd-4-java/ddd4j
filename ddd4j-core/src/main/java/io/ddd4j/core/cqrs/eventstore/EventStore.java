package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.List;

/**
 * 事件存储 SPI（ADR-0005）。
 *
 * <p>框架无关的四方法同步接口：API 形态对齐外部构件 esc-api（cqrs-4-java 参照系）
 * 的已验证语义，但完全独立实现（no code reuse）。与 esc-api 的关键差异见
 * docs/reference/fuin-api-patterns/05-event-store.md「ddd4j 自研决策」节：
 * <ul>
 *   <li>流标识为 {@code (aggregateType, AggregateRootId)} 直接参数对，替代 {@code StreamId}</li>
 *   <li>无 {@code open()/close()} 生命周期——资源管理交各运行时容器（ADR-0003）</li>
 *   <li>无 {@code deleteStream}——删除走墓碑领域事件的统一追加路径</li>
 *   <li>无不带 {@code expectedVersion} 的追加重载——乐观锁不可选关闭</li>
 *   <li>{@code readAll} 按全局 {@code position} 读取，补齐 esc-api 缺失的全局顺序</li>
 * </ul>
 *
 * <h3>乐观锁</h3>
 * <p>append 时校验 {@code expectedVersion}（期望的流当前版本号，空流为 0），
 * 与实际版本不一致时抛 {@link AggregateVersionConflictException}。
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>JPA：{@code ddd4j-data-event-store-jpa}</li>
 *   <li>Quarkus Panache：{@code ddd4j-data-event-store-panache}</li>
 *   <li>Javalin JDBI：{@code ddd4j-data-event-store-jdbi}</li>
 *   <li>响应式（Reactor 单轨）：{@code ddd4j-data-event-store-r2dbc} 走 {@code AsyncEventStore}</li>
 * </ul>
 * 生命周期不入 SPI：实现按各运行时容器惯例装配，无隐式 open。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface EventStore {

    /**
     * 追加事件到聚合流。
     *
     * <p>实现须完成乐观锁校验（expectedVersion＋事件数 == nextVersion 的一致性断言在实现内部做），
     * 并为每个事件分配全局递增 {@code position} 后持久化。
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合 ID
     * @param events          要追加的事件（非空列表）
     * @param expectedVersion 期望的当前版本号（乐观锁，空流为 0）
     * @throws AggregateVersionConflictException 版本冲突
     */
    void append(String aggregateType, AggregateRootId aggregateId,
                List<? extends DomainEvent<?>> events, long expectedVersion);

    /**
     * 读取聚合全部事件。
     *
     * <p>流不存在时返回空列表（读侧轻量状态探测思想，不单列 exists/state 方法）。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @return 按版本升序的持久化事件；无事件时为空列表
     */
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);

    /**
     * 读取指定版本区间的事件。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param fromVersion   起始版本号（含）
     * @param toVersion     结束版本号（含）
     * @return 版本区间内的持久化事件，按版本升序
     */
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                           long fromVersion, long toVersion);

    /**
     * 读取全局事件流（用于 projection）。
     *
     * <p>{@code position} 是跨所有聚合流全局递增的序号，投影以「上次处理到的 position」
     * 为断线续传位点，循环调用本方法直至读取数小于 {@code limit}。
     *
     * @param fromPosition 起始 position（含）
     * @param limit        最大读取数量
     * @return position 升序的持久化事件
     */
    List<StoredEvent> readAll(long fromPosition, int limit);
}
