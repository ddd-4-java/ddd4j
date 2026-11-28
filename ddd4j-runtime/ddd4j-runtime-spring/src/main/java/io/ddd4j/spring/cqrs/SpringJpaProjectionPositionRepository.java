package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 投影位置仓储（{@link ProjectionPositionRepository} SPI 实现）。
 *
 * <p>由 Spring 框架自动注入，业务方无需关心。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Repository
public class SpringJpaProjectionPositionRepository
        implements ProjectionPositionRepository {

    /**
     * Spring Data JPA 仓库代理
     */
    private final JpaRepository<SpringJpaProjectionPosition, String> jpaRepository;

    /**
     * \u521b\u5efa\u57fa\u4e8e Spring Data JPA \u7684\u6295\u5f71\u4f4d\u7f6e\u4ed3\u5e93\u3002
     * @param jpaRepository Spring Data JPA \u4ed3\u5e93
     */
    public SpringJpaProjectionPositionRepository(JpaRepository<SpringJpaProjectionPosition, String> jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>jpaRepository.findById(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return jpaRepository.findById(streamId).map(p -> (ProjectionPosition) p);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>JPA 实现调用 <code>jpaRepository.findAll().stream()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return jpaRepository.findAll().stream().map(p -> (ProjectionPosition) p).toList();
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>将接口位置转换为 JPA 实体，同时支持首次推进时创建的默认位置值对象。</p>
     *
     * @param position 投影位置
     * @return 持久化后的投影位置实体
     */
    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        SpringJpaProjectionPosition entity = position instanceof SpringJpaProjectionPosition
                ? (SpringJpaProjectionPosition) position
                : new SpringJpaProjectionPosition(position.getStreamId(), position.getNextEventNumber());
        return jpaRepository.save(entity);
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>jpaRepository.deleteById(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void deleteByStreamId(String streamId) {
        jpaRepository.deleteById(streamId);
    }

    /**
     * \u5728\u4e8b\u52a1\u4e2d\u91cd\u7f6e\u5df2\u6709\u4f4d\u7f6e\uff1b\u6d41\u5c1a\u4e0d\u5b58\u5728\u65f6\u521b\u5efa\u96f6\u4f4d\u7f6e\u3002
     * @param streamId \u6295\u5f71\u6d41 ID
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        SpringJpaProjectionPosition position = jpaRepository.findById(streamId)
                .orElseGet(() -> new SpringJpaProjectionPosition(streamId, 0L));
        position.setNextEventNumber(0L);
        jpaRepository.save(position);
    }
}
