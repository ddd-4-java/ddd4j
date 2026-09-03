package io.ddd4j.data.projection.panache;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Quarkus Hibernate ORM Panache 的 {@link ProjectionPositionRepository} 实现
 * （core SPI，位于 {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>适用于 Quarkus 3.x 运行时；Spring 系运行时请用 {@code ddd4j-data-projection-jpa}。
 * 本类只做「core SPI 语义 ↔ Panache 持久化原语」的适配组装：active record 实体
 * （{@link PanacheProjectionPositionEntity}）承载列映射与静态查询原语，值对象映射
 * （实体 ↔ 不可变 {@link DefaultProjectionPosition}）、缺行插入零位、upsert 语义在本层完成。
 *
 * <p><b>不注入实体</b>：Panache active record 的持久化原语是实体类上的静态方法
 * （{@code find/listAll/persist/delete}），实体非 CDI Bean，ArC 无从注入——与
 * {@code PanacheEventStore}（-panache 事件存储模块）同款：适配器直接静态委托，
 * 构造器无协作者Bean，故无注入参数。
 *
 * <p>生命周期不入 SPI（ADR-0003）：写路径的持久化原语须活动事务，由
 * {@code jakarta.transaction.Transactional}（Quarkus Narayana JTA，勿与 Spring 的
 * 同名注解混用）声明式管理；读路径无需事务包装。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see PanacheProjectionPositionEntity
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * {@inheritDoc}
     *
     * <p>实体转不可变值对象返回（不泄漏持久化实体到领域层）。
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        // 显式局部变量类型：Panache 静态泛型 find 链式推断兜底问题（同实体 upsert 注）
        PanacheProjectionPositionEntity entity = PanacheProjectionPositionEntity
                .find("streamId", streamId).firstResult();
        return Optional.ofNullable(entity)
                .map(found -> (ProjectionPosition) new DefaultProjectionPosition(
                        found.streamId, found.nextEventNumber));
    }

    @Override
    public List<ProjectionPosition> findAll() {
        List<PanacheProjectionPositionEntity> entities = PanacheProjectionPositionEntity.listAll();
        return entities.stream()
                .map(entity -> (ProjectionPosition) new DefaultProjectionPosition(
                        entity.streamId, entity.nextEventNumber))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>upsert 语义：行不存在则插入（{@code ProjectionDispatcher} 首次推进
     * 「缺行按 0 推进再 save」的路径依赖此行为），已存在则原位更新位置计数。
     *
     * @return 持久化后的不可变位置值
     */
    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        PanacheProjectionPositionEntity.upsert(position.getStreamId(), position.getNextEventNumber());
        return new DefaultProjectionPosition(position.getStreamId(), position.getNextEventNumber());
    }

    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        PanacheProjectionPositionEntity.delete("streamId", streamId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>委托实体 {@link PanacheProjectionPositionEntity#resetToZero}：行存在则回退 0，
     * 不存在则插入零位行（保证 reset 后 {@link #findByStreamId} 可读到 0 而非空，
     * 与 core {@code InMemoryProjectionPositionRepository} 同款语义）。
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        PanacheProjectionPositionEntity.resetToZero(streamId);
    }
}
