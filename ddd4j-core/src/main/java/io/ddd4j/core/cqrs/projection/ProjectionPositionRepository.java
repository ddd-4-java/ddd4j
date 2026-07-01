package io.ddd4j.core.cqrs.projection;

import java.util.List;
import java.util.Optional;

/**
 * 投影位置仓储 SPI（纯 Java）。
 *
 * <p>由各框架适配层实现：
 * <ul>
 *   <li>{@code ddd4j-adapter-spring}：基于 JPA {@code JpaRepository<ProjectionPosition, String>}</li>
 *   <li>{@code ddd4j-adapter-quarkus}：基于 Panache {@code PanacheRepositoryBase<ProjectionPosition, String>}</li>
 *   <li>{@code ddd4j-javalin}：基于 JDBI {@code @RegisterBeanMapper}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionPosition
 * @see ViewManager
 * @since 2.0.x
 */
public interface ProjectionPositionRepository {

    /**
     * 根据流 ID 查找投影位置。
     *
     * @param streamId 投影流 ID
     * @return 投影位置（不存在时返回 {@link Optional#empty()}）
     */
    Optional<ProjectionPosition> findByStreamId(String streamId);

    /**
     * 列出全部投影位置。
     */
    List<ProjectionPosition> findAll();

    /**
     * 保存或更新投影位置。
     *
     * @param position 投影位置
     * @return 持久化后的投影位置
     */
    ProjectionPosition save(ProjectionPosition position);

    /**
     * 删除指定投影位置。
     *
     * @param streamId 投影流 ID
     */
    void deleteByStreamId(String streamId);

    /**
     * 重置指定投影位置到 0（重新拉取全量事件）。
     *
     * @param streamId 投影流 ID
     */
    void resetToZero(String streamId);
}
