package io.ddd4j.core.domain.query.projection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版投影位置仓储。
 *
 * <p>适合本地开发、单元测试、Javalin/Guice 默认启动场景。生产环境应由框架适配层
 * 或业务侧替换为数据库、Redis 等持久化实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class InMemoryProjectionPositionRepository implements ProjectionPositionRepository {

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
        save(DefaultProjectionPosition.zero(streamId));
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
