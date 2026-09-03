package io.ddd4j.data.projection.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link ProjectionPositionEntity} 的 Spring Data JPA 仓储。
 *
 * <p>命名带 {@code SpringData} 前缀以区别于 core SPI 层的领域概念（与
 * {@code ddd4j-data-event-store-jpa} 的 {@code SpringDataStoredEventRepository} 同款约定）——
 * 本接口仅承担 JPA 实体的持久化原语（CRUD 由 {@link JpaRepository} 继承），
 * 投影位置语义（值对象映射、缺行归零、upsert）由上层 {@link JpaProjectionPositionRepository}
 * 适配器组装。
 *
 * <p>{@link #incrementBy} 与 {@link #resetToZero} 均为数据库端原子更新 SQL（实体列刻意
 * non-versionable，不依赖托管态 flush），并开启 {@code clearAutomatically} 保证同一事务内
 * 后续读看到更新后的值。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionPositionEntity
 * @see JpaProjectionPositionRepository
 * @since 2.0.x
 */
public interface SpringDataProjectionPositionRepository extends JpaRepository<ProjectionPositionEntity, String> {

    /**
     * 数据库端原子自增指定流的位置计数（SPI 扩展原语，供追赶消费的批量推进用）。
     *
     * @param streamId 投影流 ID
     * @param delta    自增量（正数）
     * @return 实际更新行数（流不存在时为 0）
     */
    @Modifying(clearAutomatically = true)
    @Query("update ProjectionPositionEntity p set p.nextEventNumber = p.nextEventNumber + :delta "
            + "where p.streamId = :streamId")
    int incrementBy(@Param("streamId") String streamId, @Param("delta") long delta);

    /**
     * 数据库端将指定流的位置计数重置为 0（重新拉取全量事件）。
     *
     * @param streamId 投影流 ID
     * @return 实际更新行数（流不存在时为 0）
     */
    @Modifying(clearAutomatically = true)
    @Query("update ProjectionPositionEntity p set p.nextEventNumber = 0 where p.streamId = :streamId")
    int resetToZero(@Param("streamId") String streamId);
}
