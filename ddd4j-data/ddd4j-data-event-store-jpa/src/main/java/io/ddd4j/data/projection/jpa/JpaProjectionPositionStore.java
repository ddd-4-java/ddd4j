package io.ddd4j.data.projection.jpa;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 投影位置的公共 JPA 操作，支持保留各平台既有实体及 JPQL 名称。
 * 不启动、提交或关闭事务；实体映射及 EntityManager 生命周期由接入层选择。
 *
 * @param <E> 既有投影位置实体类型，具有 streamId 和 nextEventNumber 属性
 */
public final class JpaProjectionPositionStore<E> {
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private final EntityManager entityManager;
    /** JPA \u6295\u5f71\u68c0\u67e5\u70b9\u5b9e\u4f53\u7684 Java \u7c7b\u578b\u3002 */
    private final Class<E> entityType;
    /** JPQL \u67e5\u8be2\u4e2d\u4f7f\u7528\u7684\u68c0\u67e5\u70b9\u5b9e\u4f53\u540d\u79f0\u3002 */
    private final String entityName;

    /**
     * 创建共享持久化操作。
     *
     * @param entityManager 调用方管理的实体管理器
     * @param entityType    已注册的实体类型
     * @param entityName    可信映射中的 JPQL 实体名，不接收用户输入
     */
    public JpaProjectionPositionStore(EntityManager entityManager, Class<E> entityType, String entityName) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.entityName = Objects.requireNonNull(entityName, "entityName must not be null");
    }

    /**
     * @param streamId 流标识
     * @return 对应的受管实体（如果存在）
     */
    public Optional<E> findByStreamId(String streamId) {
        return Optional.ofNullable(entityManager.find(entityType, streamId));
    }

    /**
     * @return JPA 查询得到的实体列表，快照策略由接入层决定
     */
    public List<E> findAll() {
        return entityManager.createQuery("select p from " + entityName + " p", entityType).getResultList();
    }

    /**
     * @param streamId 实体主键
     * @param entity   待保存实体
     * @return 保存后的受管实体
     */
    public E save(String streamId, E entity) {
        if (findByStreamId(streamId).isEmpty()) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    /**
     * @param streamId 流标识；不存在时不执行删除
     */
    public void deleteByStreamId(String streamId) {
        findByStreamId(streamId).ifPresent(entityManager::remove);
    }

    /**
     * 原位重置已有行，不创建缺失行，也不清空调用方持久化上下文。
     *
     * @param streamId 流标识
     * @return 更新行数；调用方负责处理已加载实体及缺行语义
     */
    public int resetToZero(String streamId) {
        return entityManager.createQuery("update " + entityName + " p set p.nextEventNumber = 0 "
                        + "where p.streamId = :streamId")
                .setParameter("streamId", streamId).executeUpdate();
    }
}
