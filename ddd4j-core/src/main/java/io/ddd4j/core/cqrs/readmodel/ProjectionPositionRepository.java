package io.ddd4j.core.cqrs.readmodel;

import java.util.List;
import java.util.Optional;

/**
 * 投影位置仓储 SPI（纯 Java）。
 *
 * <p>由各框架适配层实现：
 * <ul>
 *   <li>{@code ddd4j-runtime-spring}：基于 JPA {@code JpaRepository<ProjectionPosition, String>}</li>
 *   <li>{@code ddd4j-runtime-quarkus}：基于 Panache {@code PanacheRepositoryBase<ProjectionPosition, String>}</li>
 *   <li>{@code ddd4j-javalin}：基于 JDBI {@code @RegisterBeanMapper}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionPosition
 * @see ViewManager
 * @since 2.0.x
 */
public interface ProjectionPositionRepository {
    Optional<ProjectionPosition> findByStreamId(String streamId);
    List<ProjectionPosition> findAll();
    ProjectionPosition save(ProjectionPosition position);
    void deleteByStreamId(String streamId);
    void resetToZero(String streamId);
}
