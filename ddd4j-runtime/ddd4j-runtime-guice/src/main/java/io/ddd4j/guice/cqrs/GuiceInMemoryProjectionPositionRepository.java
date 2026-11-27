package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Guice 环境默认的内存版投影位置仓储。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuiceInMemoryProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * 投影位置内存存储
     */
    private final ConcurrentMap<String, ProjectionPosition> store = new ConcurrentHashMap<>();

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>Optional.ofNullable(store.get(streamId))</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(store.get(streamId));
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>此实现调用 <code>List.copyOf(store.values())</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return List.copyOf(store.values());
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>store.put(position.getStreamId(), position)</code>。</p>
     *
     * @param position 投影位置
     * @return 实际返回<code>position</code>
     */
    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        store.put(position.getStreamId(), position);
        return position;
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>store.remove(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void deleteByStreamId(String streamId) {
        store.remove(streamId);
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>此实现调用 <code>store.get(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void resetToZero(String streamId) {
        ProjectionPosition old = store.get(streamId);
        if (Objects.nonNull(old)) {
            store.put(streamId, old.withNextEventNumber(0L));
        }
    }

    /**
     * \u83b7\u53d6\u5185\u5b58\u4e2d\u7684\u6295\u5f71\u4f4d\u7f6e\u6570\u3002
     * @return \u4f4d\u7f6e\u6570
     */
    public int size() {
        return store.size();
    }

    /** \u6e05\u7a7a\u5185\u5b58\u4e2d\u7684\u6295\u5f71\u4f4d\u7f6e\u3002 */
    public void clear() {
        store.clear();
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u6295\u5f71\u4f4d\u7f6e\u5feb\u7167\u3002
     * @return \u53ea\u8bfb\u4f4d\u7f6e\u5feb\u7167
     */
    public Map<String, ProjectionPosition> snapshot() {
        return Map.copyOf(store);
    }
}
