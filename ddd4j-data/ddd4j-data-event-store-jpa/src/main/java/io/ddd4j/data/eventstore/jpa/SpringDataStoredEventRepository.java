package io.ddd4j.data.eventstore.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link StoredEventEntity} 的 Spring Data JPA 仓储。
 *
 * <p>命名带 {@code SpringData} 前缀以区别于 EventStore SPI 层的领域概念——本接口仅承担
 * JPA 实体的持久化原语（追加/查询/当前版本），EventStore 语义（并发检查、异常翻译、
 * 序列化）由上层 {@code JpaEventStore} 适配器组装（ADR-0005）。
 *
 * <p>{@link #findCurrentVersion} 使用悲观写锁串行化“读当前版本→追加下一版本”临界区，
 * 与 {@code uk_aggregate_version} 唯一约束共同保证乐观并发语义的数据层兜底。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see StoredEventEntity
 * @since 2.0.x
 */
public interface SpringDataStoredEventRepository extends JpaRepository<StoredEventEntity, Long> {

    /**
     * 查询指定聚合的当前最大版本号（无事件时返回 0），以悲观写锁串行化并发追加。
     *
     * @param aggregateType 聚合类型名
     * @param aggregateId   聚合 ID 字符串形式
     * @return 当前版本号（0 表示无事件）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coalesce(max(s.version), 0) from StoredEventEntity s " +
            "where s.aggregateType = :type and s.aggregateId = :id")
    long findCurrentVersion(@Param("type") String aggregateType, @Param("id") String aggregateId);

    /**
     * 按版本升序加载指定聚合的全部事件。
     */
    List<StoredEventEntity> findByAggregateTypeAndAggregateIdOrderByVersionAsc(String aggregateType, String aggregateId);

    /**
     * 按版本升序加载指定聚合在 [fromVersion, toVersion] 闭区间内的事件。
     */
    List<StoredEventEntity> findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
        String aggregateType, String aggregateId, long fromVersion, long toVersion);

    /**
     * 按全局流位置升序加载 position 之后（含）的事件（订阅/追赶消费用）。
     */
    List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(long position);
}
