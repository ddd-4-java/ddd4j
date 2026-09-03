package io.ddd4j.data.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Spring Data JPA 的 {@link ProjectionPositionRepository} 实现（core SPI，
 * 位于 {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>适用于任何使用 Hibernate/JPA 的 Spring 系运行时（WebMVC/WebFlux 等）；Quarkus 请用
 * 后续 {@code ddd4j-data-projection-panache}。本类只做「core SPI 语义 ↔ JPA 持久化原语」
 * 的适配组装：实体与不可变值对象 {@link DefaultProjectionPosition} 互转、缺行归零、
 * upsert 语义，持久化原语全部由 {@link SpringDataProjectionPositionRepository} 承担。
 *
 * <p>结构上不把 core SPI 直接塞进 Spring Data 仓储接口：{@code JpaRepository#findAll}
 * 固定返回 {@code List<实体>}，与 SPI 的 {@code List<ProjectionPosition>} 泛型不可协变
 * （同一方法名无法共存），故按 {@code ddd4j-data-event-store-jpa} 的
 * 「SpringData 仓储 + Jpa 适配器」两件套拆分（ADR-0005 同款分层）。
 *
 * <p>事务由 {@code @Transactional} 声明式管理（Spring 运行时由 {@code JpaTransactionManager}
 * 装配；集成方已有事务时本层加入即可，无需额外包装门面）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see SpringDataProjectionPositionRepository
 * @see ProjectionPositionEntity
 * @since 2.0.x
 */
@Repository
public class JpaProjectionPositionRepository implements ProjectionPositionRepository {

    private final SpringDataProjectionPositionRepository repository;

    public JpaProjectionPositionRepository(SpringDataProjectionPositionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>实体转不可变值对象返回（不泄漏持久化实体到领域层）。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return repository.findById(streamId)
                .map(entity -> (ProjectionPosition) new DefaultProjectionPosition(
                        entity.getStreamId(), entity.getNextEventNumber()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectionPosition> findAll() {
        return repository.findAll().stream()
                .map(entity -> (ProjectionPosition) new DefaultProjectionPosition(
                        entity.getStreamId(), entity.getNextEventNumber()))
                .collect(java.util.stream.Collectors.toList());
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
        ProjectionPositionEntity entity = repository.findById(position.getStreamId())
                .orElseGet(() -> ProjectionPositionEntity.zero(position.getStreamId()));
        entity.setNextEventNumber(position.getNextEventNumber());
        repository.saveAndFlush(entity);
        return new DefaultProjectionPosition(position.getStreamId(), position.getNextEventNumber());
    }

    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        repository.deleteById(streamId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>走数据库端原子 UPDATE；行不存在时按 core
     * {@code InMemoryProjectionPositionRepository} 同款语义插入 0 位置
     * （保证 reset 后 {@link #findByStreamId} 可读到 0 而非空）。
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        if (repository.resetToZero(streamId) == 0) {
            repository.saveAndFlush(ProjectionPositionEntity.zero(streamId));
        }
    }

    /**
     * 数据库端原子自增指定流的位置计数并读回新值（本模块扩展，非 core SPI 方法；
     * 供追赶消费等需要「自增＋确认」原子性的场景使用）。
     *
     * @param streamId 投影流 ID
     * @param delta    自增量（正数）
     * @return 自增后的最新位置计数
     * @throws IllegalArgumentException 流不存在时（须先 {@link #save(ProjectionPosition)} 落行）
     */
    @Transactional
    public long incrementBy(String streamId, long delta) {
        if (repository.incrementBy(streamId, delta) == 0) {
            throw new IllegalArgumentException("projection position not found: " + streamId);
        }
        return repository.findById(streamId)
                .map(ProjectionPositionEntity::getNextEventNumber)
                .orElseThrow(() -> new IllegalStateException("mandatory"));
    }
}
