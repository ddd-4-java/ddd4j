package io.ddd4j.core.cqrs.readmodel;

import java.util.List;
import java.util.Optional;

/** 投影位置持久化端口。 */
public interface ProjectionPositionRepository {
    Optional<ProjectionPosition> findByStreamId(String streamId);
    List<ProjectionPosition> findAll();
    ProjectionPosition save(ProjectionPosition position);
    void deleteByStreamId(String streamId);
    void resetToZero(String streamId);
}
