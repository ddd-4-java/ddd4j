package io.ddd4j.core.cqrs.readmodel;

import java.util.Objects;

/** 基于 ProjectionPositionRepository 的默认服务实现。 */
public final class DefaultProjectionService implements ProjectionService {
    private final ProjectionPositionRepository repository;
    public DefaultProjectionService(ProjectionPositionRepository repository) { this.repository = Objects.requireNonNull(repository, "repository"); }
    @Override public void resetProjectionPosition(String streamId) { repository.resetToZero(streamId); }
    @Override public long readProjectionPosition(String streamId) { return repository.findByStreamId(streamId).map(ProjectionPosition::getNextEventNumber).orElse(0L); }
    @Override public ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber) {
        ProjectionPosition current = repository.findByStreamId(streamId).orElse(DefaultProjectionPosition.zero(streamId));
        return repository.save(current.withNextEventNumber(nextEventNumber));
    }
}
