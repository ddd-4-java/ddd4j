package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Quarkus 标准 JPA 投影位置仓储（{@link ProjectionPositionRepository} SPI 实现）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusJpaProjectionPositionRepository implements ProjectionPositionRepository {

    /** \u7528\u4e8e\u4ece CDI \u83b7\u53d6\u5f53\u524d JPA EntityManager \u7684\u67e5\u8be2\u5165\u53e3\u3002 */
    @Inject
    Instance<EntityManager> entityManagers;

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>Optional.ofNullable(manager().find(QuarkusJpaProjectionPosition.class, streamId))</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(manager().find(QuarkusJpaProjectionPosition.class, streamId))
                .map(position -> (ProjectionPosition) position);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>JPA 实现调用 <code>manager().createQuery("select p from QuarkusJpaProjectionPosition p", QuarkusJpaProjectionPosition.class)</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return manager().createQuery("select p from QuarkusJpaProjectionPosition p",
                        QuarkusJpaProjectionPosition.class)
                .getResultList().stream()
                .map(position -> (ProjectionPosition) position)
                .toList();
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>manager().find(QuarkusJpaProjectionPosition.class, entity.streamId)</code>。</p>
     *
     * @param position 投影位置
     * @return 根据执行路径返回<code>entity</code>、<code>manager().merge(entity)</code>
     */
    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        QuarkusJpaProjectionPosition entity = (QuarkusJpaProjectionPosition) position;
        QuarkusJpaProjectionPosition current = manager().find(QuarkusJpaProjectionPosition.class, entity.streamId);
        if (Objects.isNull(current)) {
            manager().persist(entity);
            return entity;
        }
        return manager().merge(entity);
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>manager().find(QuarkusJpaProjectionPosition.class, streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        QuarkusJpaProjectionPosition entity = manager().find(QuarkusJpaProjectionPosition.class, streamId);
        if (Objects.nonNull(entity)) {
            manager().remove(entity);
        }
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>JPA 实现调用 <code>manager()</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        manager().createQuery("update QuarkusJpaProjectionPosition p set p.nextEventNumber = 0 "
                        + "where p.streamId = :streamId")
                .setParameter("streamId", streamId)
                .executeUpdate();
    }

    /**
     * \u89e3\u6790\u5f53\u524d\u8c03\u7528\u5bf9\u5e94\u7684 JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u3002
     * @return \u7528\u4e8e\u6295\u5f71\u4f4d\u70b9\u8bfb\u5199\u7684\u5b9e\u4f53\u7ba1\u7406\u5668
     */
    private EntityManager manager() {
        if (entityManagers.isUnsatisfied() || entityManagers.isAmbiguous()) {
            throw new IllegalStateException("No unique EntityManager is available. Add a Quarkus ORM extension "
                    + "before using the JPA projection repository.");
        }
        return entityManagers.get();
    }
}
