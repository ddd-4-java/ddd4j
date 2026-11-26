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
    private final EntityManager entityManager;
    private final JpaProjectionPositionStore<ProjectionPositionEntity> store;

    /**
     * @param entityManager 已配置 ProjectionPositionEntity 的实体管理器，由调用方管理生命周期
     */
    public EntityManagerProjectionPositionRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.store = new JpaProjectionPositionStore<>(entityManager, ProjectionPositionEntity.class,
                "ProjectionPositionEntity");
    }

    private static ProjectionPosition snapshot(ProjectionPositionEntity entity) {
        return new DefaultProjectionPosition(entity.getStreamId(), entity.getNextEventNumber());
    }

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return store.findByStreamId(streamId).map(EntityManagerProjectionPositionRepository::snapshot);
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return store.findAll().stream().map(EntityManagerProjectionPositionRepository::snapshot).toList();
    }

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

    @Override
    public void deleteByStreamId(String streamId) {
        requireTransaction();
        store.deleteByStreamId(streamId);
        entityManager.flush();
    }

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

    private void requireTransaction() {
        if (!entityManager.isJoinedToTransaction()) {
            throw new TransactionRequiredException("an active caller-managed transaction is required");
        }
    }
}
