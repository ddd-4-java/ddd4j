package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.projection.ProjectionPosition;
import io.ddd4j.core.cqrs.projection.ProjectionPositionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Guice 环境默认的内存版投影位置仓储。
 */
public class GuiceInMemoryProjectionPositionRepository implements ProjectionPositionRepository {

    private final ConcurrentMap<String, ProjectionPosition> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(store.get(streamId));
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        store.put(position.getStreamId(), position);
        return position;
    }

    @Override
    public void deleteByStreamId(String streamId) {
        store.remove(streamId);
    }

    @Override
    public void resetToZero(String streamId) {
        ProjectionPosition old = store.get(streamId);
        if (old != null) {
            store.put(streamId, old.withNextEventNumber(0L));
        }
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    public Map<String, ProjectionPosition> snapshot() {
        return Map.copyOf(store);
    }
}
