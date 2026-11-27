package io.ddd4j.data.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TransactionRequiredException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 纯 JPA 投影位置仓库，使用既有 ProjectionPositionEntity 映射，无 Spring 容器要求。
 * 查询返回不可变位置值和不可修改列表；缺行 reset 创建零位置。
 * 写操作参与调用方已开启的事务并 flush，失败由调用方回滚；不自行提交、清空或关闭上下文。
 * 同一实体管理器可与 EventStore、业务写入共享事务。应用负责事务范围和线程隔离。
 */
public final class EntityManagerProjectionPositionRepository implements ProjectionPositionRepository {
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private final EntityManager entityManager;
    /** \u4f7f\u7528\u5f53\u524d\u5b9e\u4f53\u7ba1\u7406\u5668\u8bfb\u53d6\u548c\u5199\u5165\u6295\u5f71\u68c0\u67e5\u70b9\u7684\u7ec4\u4ef6\u3002 */
    private final JpaProjectionPositionStore<ProjectionPositionEntity> store;

    /**
     * @param entityManager 已配置 ProjectionPositionEntity 的实体管理器，由调用方管理生命周期
     */
    public EntityManagerProjectionPositionRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.store = new JpaProjectionPositionStore<>(entityManager, ProjectionPositionEntity.class,
                "ProjectionPositionEntity");
    }

    /**
     * \u590d\u5236 JPA \u5b9e\u4f53\u7684\u6d41\u6807\u8bc6\u53ca\u4e8b\u4ef6\u5e8f\u53f7\u3002
     * @param entity \u6570\u636e\u5e93\u8fd4\u56de\u7684\u6301\u4e45\u5316\u5b9e\u4f53
     * @return \u72ec\u7acb\u4e8e JPA \u5b9e\u4f53\u7684\u6295\u5f71\u4f4d\u7f6e
     */
    private static ProjectionPosition snapshot(ProjectionPositionEntity entity) {
        return new DefaultProjectionPosition(entity.getStreamId(), entity.getNextEventNumber());
    }

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>store.findByStreamId(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return store.findByStreamId(streamId).map(EntityManagerProjectionPositionRepository::snapshot);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>此实现调用 <code>store.findAll().stream()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return store.findAll().stream().map(EntityManagerProjectionPositionRepository::snapshot).toList();
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>requireTransaction()</code>。</p>
     *
     * @param position 投影位置
     * @return 实际返回<code>snapshot(entity)</code>
     */
    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        requireTransaction();
        ProjectionPositionEntity entity = store.findByStreamId(position.getStreamId())
                .orElseGet(() -> ProjectionPositionEntity.zero(position.getStreamId()));
        entity.setNextEventNumber(position.getNextEventNumber());
        store.save(position.getStreamId(), entity);
        entityManager.flush();
        return snapshot(entity);
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>requireTransaction()</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void deleteByStreamId(String streamId) {
        requireTransaction();
        store.deleteByStreamId(streamId);
        entityManager.flush();
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>此实现调用 <code>requireTransaction()</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void resetToZero(String streamId) {
        requireTransaction();
        // 先保留同事务未 flush 的业务变更，再执行批量更新；仅刷新目标行，避免清空整个上下文。
        entityManager.flush();
        if (store.resetToZero(streamId) == 0) {
            store.save(streamId, ProjectionPositionEntity.zero(streamId));
            entityManager.flush();
        } else {
            store.findByStreamId(streamId).ifPresent(entityManager::refresh);
        }
    }

    /**
     * 数据库端自增已有流，并刷新该行保证读回最新值。
     *
     * @param streamId 已存在的流标识
     * @param delta    增量，保留旧入口接受零或负数的行为
     * @return 更新后位置
     * @throws IllegalArgumentException 流不存在
     */
    public long incrementBy(String streamId, long delta) {
        requireTransaction();
        entityManager.flush();
        int updated = entityManager.createQuery("update ProjectionPositionEntity p "
                        + "set p.nextEventNumber = p.nextEventNumber + :delta where p.streamId = :streamId")
                .setParameter("streamId", streamId).setParameter("delta", delta).executeUpdate();
        if (updated == 0) {
            throw new IllegalArgumentException("projection position not found: " + streamId);
        }
        ProjectionPositionEntity entity = store.findByStreamId(streamId).orElseThrow(
                () -> new IllegalStateException("projection position disappeared after increment: " + streamId));
        entityManager.refresh(entity);
        return entity.getNextEventNumber();
    }

    /**
     * \u8981\u6c42\u5f53\u524d\u5b9e\u4f53\u7ba1\u7406\u5668\u5df2\u52a0\u5165\u4e8b\u52a1\u4ee5\u5b89\u5168\u5199\u5165\u68c0\u67e5\u70b9\u3002
     * @throws TransactionRequiredException \u672a\u52a0\u5165\u8c03\u7528\u65b9\u4e8b\u52a1
     */
    private void requireTransaction() {
        if (!entityManager.isJoinedToTransaction()) {
            throw new TransactionRequiredException("an active caller-managed transaction is required");
        }
    }
}
