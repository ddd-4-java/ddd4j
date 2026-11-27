package io.ddd4j.core.cqrs.readmodel;

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

    /** \u6309\u4e8b\u4ef6\u6d41\u6807\u8bc6\u4fdd\u5b58\u6700\u8fd1\u8bfb\u53d6\u4f4d\u7f6e\u7684\u5185\u5b58\u6620\u5c04\u3002 */
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
     * <p>此实现调用 <code>save(DefaultProjectionPosition.zero(streamId))</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void resetToZero(String streamId) {
        save(DefaultProjectionPosition.zero(streamId));
    }

    /**
     * \u7edf\u8ba1\u5185\u5b58\u4e2d\u4fdd\u5b58\u7684\u6295\u5f71\u4f4d\u7f6e\u6570\u3002
     * @return \u6295\u5f71\u4f4d\u7f6e\u6570
     */
    public int size() {
        return store.size();
    }

    /** \u6e05\u7a7a\u5185\u5b58\u4e2d\u7684\u5168\u90e8\u6295\u5f71\u4f4d\u7f6e\u3002 */
    public void clear() {
        store.clear();
    }

    /**
     * \u590d\u5236\u5f53\u524d\u6295\u5f71\u4f4d\u7f6e\u6620\u5c04\u3002
     * @return \u5f53\u524d\u6620\u5c04\u5feb\u7167
     */
    public Map<String, ProjectionPosition> snapshot() {
        return Map.copyOf(store);
    }
}
