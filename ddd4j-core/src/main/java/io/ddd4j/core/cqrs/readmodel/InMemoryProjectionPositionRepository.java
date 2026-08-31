package io.ddd4j.core.cqrs.readmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 线程安全内存投影位置仓储，适用于测试和无持久化运行时。 */
public final class InMemoryProjectionPositionRepository implements ProjectionPositionRepository {
    private final ConcurrentMap<String, ProjectionPosition> positions = new ConcurrentHashMap<String, ProjectionPosition>();
    @Override public Optional<ProjectionPosition> findByStreamId(String streamId) { return Optional.ofNullable(positions.get(streamId)); }
    @Override public List<ProjectionPosition> findAll() { return new ArrayList<ProjectionPosition>(positions.values()); }
    @Override public ProjectionPosition save(ProjectionPosition position) { positions.put(position.getStreamId(), position); return position; }
    @Override public void deleteByStreamId(String streamId) { positions.remove(streamId); }
    @Override public void resetToZero(String streamId) { positions.put(streamId, DefaultProjectionPosition.zero(streamId)); }
}
