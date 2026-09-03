package io.ddd4j.data.projection.r2dbc;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 基于 R2DBC 的 {@link ProjectionPositionRepository} 实现（core SPI，位于
 * {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>纯 {@code io.r2dbc.spi} 落地：全部读写经 {@code Connection} 原语组装为
 * Mono／Flux，<b>零 Spring</b>——WebFlux 与 Vert.x 等响应式运行时皆可直接装配
 * （ADR-0003 多运行时对齐）。core SPI 是同步接口，故本类提供<b>双面 API</b>：
 * <ul>
 *   <li>{@code *Reactive} 公共方法（Mono／Flux）：响应式运行时直组零阻塞，
 *       连接以 {@code Mono/Flux.usingWhen} 生命周期托管（终结／出错／取消即 close）；</li>
 *   <li>同步桥接方法（SPI 实现）：内部 {@code block()} 等待单条语句完成——
 *       每方法恰一条原子语句，无跨语句事务需求（见下），桥接无一致性代价。</li>
 * </ul>
 *
 * <h3>集成方装配</h3>
 * <p>本类为纯类（零容器注解、非容器托管）：集成方在应用装配代码中手动
 * {@code new R2dbcProjectionPositionRepository(connectionFactory)} 并自行管理生命周期——
 * 构造参数取 {@link ConnectionFactory}（SPI 接口）而非 r2dbc-pool 的
 * {@code ConnectionPool}：后者 {@code implements ConnectionFactory}，池化实例
 * 直接可传，且本模块保持零 r2dbc-pool 依赖。生命周期不入 SPI（ADR-0003）：
 * 连接按操作粒度 create／close，无隐式全局状态。
 *
 * <h3>表契约</h3>
 * <p>目标表 {@code ddd4j_projection_position} 与 -jpa/-panache/-jdbi 模块同构
 * （{@code stream_id VARCHAR(250)} 自然主键＋{@code next_event_number BIGINT}，
 * 无时间戳列——5.4 事件存储的 {@code created_at} OffsetDateTime 行类型实证课题
 * 在本表不出现），DDL 由集成方负责建表。save 的 upsert 用 H2 原生
 * {@code MERGE INTO ... KEY} 单语句原子完成——实证（H2 2.4.240）：
 * {@code INSERT ... ON CONFLICT DO UPDATE} 在 H2 常规模式抛
 * {@code JdbcSQLSyntaxErrorException [42000-240]}，故弃 PostgreSQL 方言取 MERGE INTO
 * （PostgreSQL 运行时由集成方按需换 {@code ON CONFLICT} 变体）。单语句原子性
 * 由数据库保证，无需显式 beginTransaction／commit。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionPosition
 * @see DefaultProjectionPosition
 * @since 2.0.x
 */
public class R2dbcProjectionPositionRepository implements ProjectionPositionRepository {

    /** 行读取列集（与表契约一致：自然主键＋位置计数）。 */
    private static final String SELECT_COLUMNS =
            "select stream_id, next_event_number from ddd4j_projection_position";

    /**
     * 单语句原子 upsert（H2 语法，{@code $n} 占位符——r2dbc-h2 绑参约定，
     * 与 ddd4j-data-event-store-r2dbc 实证一致）。
     */
    private static final String MERGE_SQL = "merge into ddd4j_projection_position "
            + "(stream_id, next_event_number) key (stream_id) values ($1, $2)";

    private final ConnectionFactory connectionFactory;

    /**
     * 创建 R2DBC 投影位置仓储。
     *
     * @param connectionFactory R2DBC 连接工厂（集成方装配，可为 r2dbc-pool 池化实例）
     */
    public R2dbcProjectionPositionRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    /**
     * 按流 ID 查找投影位置（响应式面）。
     *
     * @param streamId 投影流 ID
     * @return 位置值（缺行发射 {@link Optional#empty()}）
     */
    public Mono<Optional<ProjectionPosition>> findByStreamIdReactive(String streamId) {
        return query(SELECT_COLUMNS + " where stream_id = $1", statement -> statement.bind(0, streamId))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * 列出全部投影位置（响应式面）。
     *
     * @return 全部位置的流
     */
    public Flux<ProjectionPosition> findAllReactive() {
        return query(SELECT_COLUMNS, statement -> statement);
    }

    /**
     * 保存或更新投影位置（响应式面）：单条 MERGE INTO 原子 upsert——行不存在则插入
     * （{@code ProjectionDispatcher} 首次推进「缺行按 0 推进再 save」的路径依赖此行为），
     * 已存在则原位更新位置计数，无「先查后写」竞态窗口。
     *
     * @param position 投影位置
     * @return 持久化后的不可变位置值
     */
    public Mono<ProjectionPosition> saveReactive(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        return upsert(position.getStreamId(), position.getNextEventNumber());
    }

    /**
     * 删除指定投影位置（响应式面）：缺行删除 0 行、静默成功（无异常契约）。
     *
     * @param streamId 投影流 ID
     * @return 完成信号
     */
    public Mono<Void> deleteByStreamIdReactive(String streamId) {
        return executeUpdate("delete from ddd4j_projection_position where stream_id = $1",
                statement -> statement.bind(0, streamId));
    }

    /**
     * 重置投影位置到 0（响应式面）：按 core {@code InMemoryProjectionPositionRepository}
     * 同款语义实现 {@code save(zero(streamId))}——缺行 reset 后插入零位行
     * （保证 reset 后 {@link #findByStreamIdReactive(String)} 可读到 0 而非空），
     * MERGE INTO 的 upsert 语义天然覆盖两分支。
     *
     * @param streamId 投影流 ID
     * @return 完成信号
     */
    public Mono<Void> resetToZeroReactive(String streamId) {
        return upsert(streamId, 0L).then();
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步桥接：内部 {@code block()} 等待响应式面完成（单语句，见类注释）。
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return findByStreamIdReactive(streamId).block();
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步桥接：内部 {@code block()} 等待响应式面完成（单语句，见类注释）。
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return findAllReactive().collectList().block();
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步桥接：内部 {@code block()} 等待响应式面完成（单语句，见类注释）。
     */
    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        return saveReactive(position).block();
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步桥接：内部 {@code block()} 等待响应式面完成（单语句，见类注释）。
     */
    @Override
    public void deleteByStreamId(String streamId) {
        deleteByStreamIdReactive(streamId).block();
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步桥接：内部 {@code block()} 等待响应式面完成（单语句，见类注释）。
     */
    @Override
    public void resetToZero(String streamId) {
        resetToZeroReactive(streamId).block();
    }

    /**
     * 单语句原子落位（save／resetToZero 共用）。
     *
     * @param streamId        投影流 ID（自然主键）
     * @param nextEventNumber 位置计数
     * @return 持久化后的不可变位置值
     */
    private Mono<ProjectionPosition> upsert(String streamId, long nextEventNumber) {
        return executeUpdate(MERGE_SQL, statement -> statement
                        .bind(0, streamId)
                        .bind(1, nextEventNumber))
                .thenReturn(new DefaultProjectionPosition(streamId, nextEventNumber));
    }

    /**
     * 读路径通用组装：连接以 {@code Flux.usingWhen} 托管（读流终结／出错／取消即
     * close），SQL 经 binder 绑参后执行并逐行重建不可变位置值。
     *
     * @param sql    查询 SQL（占位符 {@code $n}）
     * @param binder 占位符绑参
     * @return 行重建的位置值流
     */
    private Flux<ProjectionPosition> query(String sql, UnaryOperator<Statement> binder) {
        return Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(binder.apply(connection.createStatement(sql)).execute())
                        .flatMap(result -> result.map((row, metadata) -> (ProjectionPosition)
                                new DefaultProjectionPosition(
                                        row.get("stream_id", String.class),
                                        row.get("next_event_number", Long.class)))),
                connection -> Mono.from(connection.close()),
                (connection, ex) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close()));
    }

    /**
     * 写路径通用组装：连接以 {@code Mono.usingWhen} 托管，完成信号即成功。
     *
     * @param sql    DML 语句（占位符 {@code $n}）
     * @param binder 占位符绑参
     * @return 完成信号
     */
    private Mono<Void> executeUpdate(String sql, UnaryOperator<Statement> binder) {
        return Mono.usingWhen(
                connectionFactory.create(),
                connection -> Mono.from(binder.apply(connection.createStatement(sql)).execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .then(),
                connection -> Mono.from(connection.close()),
                (connection, ex) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close()));
    }
}
