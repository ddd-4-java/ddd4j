package io.ddd4j.data.projection.jdbi;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBI 的 {@link ProjectionPositionRepository} 实现（core SPI，位于
 * {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>SQL-first：全部读写以手写 SQL 经 jdbi3-core 的 Statement/Handle 原语执行
 * （无注解 SQL Object），适用于 Javalin／Vert.x 等轻量运行时；Spring 系运行时请用
 * {@code ddd4j-data-projection-jpa}，Quarkus 请用 {@code ddd4j-data-projection-panache}，
 * 响应式请用 {@code ddd4j-data-projection-r2dbc}。
 *
 * <h3>集成方装配</h3>
 * <p>本类为纯类（零容器注解、非容器托管）：Javalin／Vert.x 集成方在应用装配代码中
 * 手动 {@code new JdbiProjectionPositionRepository(jdbi)} 并自行管理其生命周期——
 * {@code Jdbi} 实例（可包连接池 DataSource）由集成方提供。生命周期不入 SPI
 * （ADR-0003）：连接资源由 {@code Jdbi} 底层 DataSource 管理，每方法一次
 * {@link Jdbi#withHandle}／{@link Jdbi#useHandle}，无隐式 open/close。
 *
 * <h3>表契约</h3>
 * <p>目标表 {@code ddd4j_projection_position} 与 -jpa/-panache 模块同构
 * （{@code stream_id VARCHAR(250)} 自然主键＋{@code next_event_number BIGINT}），
 * DDL 由集成方负责建表。save 的 upsert 用 H2 原生 {@code MERGE INTO ... KEY}
 * 单语句原子完成——实证（H2 2.4.240 Shell）：常规模式下
 * {@code INSERT ... ON CONFLICT (stream_id) DO UPDATE} 抛
 * {@code JdbcSQLSyntaxErrorException [42000-240]}，MERGE INTO 则两次执行后
 * 原位更新（1 行、计数原位变化），故选 MERGE INTO。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionPosition
 * @see DefaultProjectionPosition
 * @since 2.0.x
 */
public class JdbiProjectionPositionRepository implements ProjectionPositionRepository {

    /** 行读取列集（与表契约一致：自然主键＋位置计数）。 */
    private static final String SELECT_COLUMNS = "select stream_id, next_event_number from ddd4j_projection_position";

    /**
     * 单语句原子 upsert（H2 语法；PostgreSQL 等方言运行时由集成方按需替换为
     * {@code INSERT ... ON CONFLICT (stream_id) DO UPDATE}）。
     */
    private static final String MERGE_SQL = "merge into ddd4j_projection_position "
            + "(stream_id, next_event_number) key (stream_id) values (:streamId, :next)";

    private final Jdbi jdbi;

    /**
     * 创建 JDBI 投影位置仓储。
     *
     * @param jdbi JDBI 实例（集成方装配，可包连接池 DataSource）
     */
    public JdbiProjectionPositionRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>行重建为不可变值对象 {@link DefaultProjectionPosition}（不暴露原始行），
     * 缺行返回 {@link Optional#empty()}。
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        SELECT_COLUMNS + " where stream_id = :id")
                .bind("id", streamId)
                .map((rs, ctx) -> (ProjectionPosition) new DefaultProjectionPosition(
                        rs.getString("stream_id"), rs.getLong("next_event_number")))
                .findOne());
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return jdbi.withHandle(handle -> handle.createQuery(SELECT_COLUMNS)
                .map((rs, ctx) -> (ProjectionPosition) new DefaultProjectionPosition(
                        rs.getString("stream_id"), rs.getLong("next_event_number")))
                .list());
    }

    /**
     * {@inheritDoc}
     *
     * <p>upsert 语义：行不存在则插入（{@code ProjectionDispatcher} 首次推进
     * 「缺行按 0 推进再 save」的路径依赖此行为），已存在则原位更新位置计数——
     * 单条 MERGE INTO 原子完成（无「先查后写」竞态窗口）。
     *
     * @return 持久化后的不可变位置值
     */
    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        return upsert(position.getStreamId(), position.getNextEventNumber());
    }

    /**
     * {@inheritDoc}
     *
     * <p>缺行时删除 0 行、静默成功（无异常契约）；与 -jpa 模块差异说明：
     * 本实现不区分「删存在行」与「删缺行」，两者皆无异常。
     */
    @Override
    public void deleteByStreamId(String streamId) {
        jdbi.useHandle(handle -> handle.createUpdate(
                        "delete from ddd4j_projection_position where stream_id = :id")
                .bind("id", streamId)
                .execute());
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 core {@code InMemoryProjectionPositionRepository} 同款语义实现：
     * {@code save(zero(streamId))}——缺行 reset 后插入零位行（保证 reset 后
     * {@link #findByStreamId} 可读到 0 而非空），MERGE INTO 的 upsert 语义
     * 天然覆盖两分支。
     */
    @Override
    public void resetToZero(String streamId) {
        upsert(streamId, 0L);
    }

    /**
     * 单语句原子落位（save／resetToZero 共用）。
     *
     * @param streamId        投影流 ID（自然主键）
     * @param nextEventNumber 位置计数
     * @return 持久化后的不可变位置值
     */
    private ProjectionPosition upsert(String streamId, long nextEventNumber) {
        jdbi.useHandle(handle -> handle.createUpdate(MERGE_SQL)
                .bind("streamId", streamId)
                .bind("next", nextEventNumber)
                .execute());
        return new DefaultProjectionPosition(streamId, nextEventNumber);
    }
}
