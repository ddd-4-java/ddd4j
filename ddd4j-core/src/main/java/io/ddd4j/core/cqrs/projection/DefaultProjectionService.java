package io.ddd4j.core.cqrs.projection;

import java.util.Objects;

/**
 * 基于 {@link ProjectionPositionRepository} 的默认投影位置服务。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class DefaultProjectionService implements ProjectionService {

    private final ProjectionPositionRepository repository;

    public DefaultProjectionService(ProjectionPositionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public void resetProjectionPosition(String streamId) {
        repository.resetToZero(streamId);
    }

    @Override
    public long readProjectionPosition(String streamId) {
        return repository.findByStreamId(streamId)
                .map(ProjectionPosition::getNextEventNumber)
                .orElse(0L);
    }

    @Override
    public ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber) {
        ProjectionPosition current = repository.findByStreamId(streamId)
                .orElseGet(() -> DefaultProjectionPosition.zero(streamId));
        return repository.save(current.withNextEventNumber(nextEventNumber));
    }
}
