package io.ddd4j.data.projection.r2dbc;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link R2dbcProjectionPositionRepository} 纯 r2dbc-h2＋H2 全量契约集成测试
 * （Task 7.5，本地必跑轨）。
 *
 * <p>零容器：{@code ConnectionFactories.get("r2dbc:h2:mem:///...")} 直连 H2 内存库，
 * DDL 在 {@code @BeforeAll} 手工执行（表结构与 -jpa/-panache/-jdbi 模块同构——
 * {@code stream_id} 自然主键＋{@code next_event_number} 计数列，DDL parity；无时间戳
 * 列，故 5.4 事件存储的 {@code created_at} 行类型实证课题在本表不出现），零 mock
 * ——完整验证 core SPI 语义↔纯 {@code io.r2dbc.spi} Connection 原语的组装：响应式面
 * （{@code *Reactive}）与同步桥接面（SPI 实现）双面覆盖——save→自增→读回往返、
 * resetToZero 回退（含缺行插入零位行）、跨投影流（handler 名）命名空间隔离、
 * upsert 与删除语义。响应式面断言全部经 {@link StepVerifier} 信号序列。
 *
 * <p>「重启读回」模拟：仓储无任何缓存（每方法独立连接直达库），断言前新建仓储
 * 实例重读——等价于进程重启后新实例的可见性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("R2dbcProjectionPositionRepository 纯 r2dbc-h2 + H2 全量契约 IT")
class ProjectionPositionR2dbcIT {

    /** 投影流 ID＝handler 名（ProjectionHandler#getName 约定）。 */
    private static final String ORDER_SUMMARY = "order-summary";

    private static final String INVENTORY_SNAPSHOT = "inventory-snapshot";

    /**
     * 与 -jpa/-panache/-jdbi 模块同构的 DDL（列集 parity：{@code stream_id}
     * VARCHAR(250) 自然主键＋{@code next_event_number} BIGINT 非空计数）。
     */
    private static final String DDL = """
            create table if not exists ddd4j_projection_position (
                stream_id varchar(250) not null primary key,
                next_event_number bigint not null
            )""";

    private static ConnectionFactory connectionFactory;

    private R2dbcProjectionPositionRepository positions;

    @BeforeAll
    static void setUpDatabase() {
        connectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///projectionit;DB_CLOSE_DELAY=-1");
        StepVerifier.create(executeUpdate(DDL, statement -> statement)).verifyComplete();
    }

    @BeforeEach
    void cleanPositions() {
        StepVerifier.create(executeUpdate("delete from ddd4j_projection_position", statement -> statement))
                .verifyComplete();
        // 每用例新建仓储实例：受测代码零状态，亦证明断言不依赖任何实例内缓存
        positions = new R2dbcProjectionPositionRepository(connectionFactory);
    }

    @Test
    void saveIncrReadBack_响应式完整循环_应跨仓储实例读回最终位置() {
        // 先落零位行（dispatcher 冷启动路径），断言 save 返回持久化后的不可变位置值
        StepVerifier.create(positions.saveReactive(DefaultProjectionPosition.zero(ORDER_SUMMARY)))
                .expectNextMatches(saved -> saved.getStreamId().equals(ORDER_SUMMARY)
                        && saved.getNextEventNumber() == 0L)
                .verifyComplete();

        // 逐事件推进到 5（每次一条 MERGE INTO 原子 upsert）
        StepVerifier.create(positions.findByStreamIdReactive(ORDER_SUMMARY)
                        .flatMap(optional -> positions.saveReactive(
                                optional.get().withNextEventNumber(1L)))
                        .then(positions.findByStreamIdReactive(ORDER_SUMMARY)
                                .flatMap(optional -> positions.saveReactive(
                                        optional.get().withNextEventNumber(2L))))
                        .then(positions.findByStreamIdReactive(ORDER_SUMMARY)
                                .flatMap(optional -> positions.saveReactive(
                                        optional.get().withNextEventNumber(3L))))
                        .then(positions.findByStreamIdReactive(ORDER_SUMMARY)
                                .flatMap(optional -> positions.saveReactive(
                                        optional.get().withNextEventNumber(4L))))
                        .then(positions.findByStreamIdReactive(ORDER_SUMMARY)
                                .flatMap(optional -> positions.saveReactive(
                                        optional.get().withNextEventNumber(5L)))))
                .expectNextMatches(position -> position.getNextEventNumber() == 5L)
                .verifyComplete();

        // 重启读回：新建仓储实例从库重读，位置仍为 5（持久性，非实例态内存值）
        StepVerifier.create(new R2dbcProjectionPositionRepository(connectionFactory)
                        .findByStreamIdReactive(ORDER_SUMMARY))
                .expectNextMatches(optional -> optional.isPresent()
                        && optional.get().getStreamId().equals(ORDER_SUMMARY)
                        && optional.get().getNextEventNumber() == 5L)
                .verifyComplete();
    }

    @Test
    void resetToZero_响应式推进后重置_应回退到0() {
        StepVerifier.create(positions.saveReactive(new DefaultProjectionPosition(ORDER_SUMMARY, 5L)))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(positions.findByStreamIdReactive(ORDER_SUMMARY))
                .expectNextMatches(optional -> optional.map(ProjectionPosition::getNextEventNumber).filter(v -> v == 5L).isPresent())
                .verifyComplete();

        StepVerifier.create(positions.resetToZeroReactive(ORDER_SUMMARY)).verifyComplete();

        assertThat(positions.findByStreamId(ORDER_SUMMARY)) // 同步桥接面顺带覆盖
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    @Test
    void resetToZero_流不存在_应插入零位行() {
        // 与 core InMemoryProjectionPositionRepository 同款语义：缺行 reset 后可读到 0
        positions.resetToZero(ORDER_SUMMARY); // 同步桥接面

        StepVerifier.create(positions.findByStreamIdReactive(ORDER_SUMMARY))
                .expectNextMatches(optional -> optional.isPresent()
                        && optional.get().getNextEventNumber() == 0L)
                .verifyComplete();
    }

    @Test
    void 两个handler命名空间_同步SPI面_分别save后独立推进互不串扰() {
        positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));
        positions.save(DefaultProjectionPosition.zero(INVENTORY_SNAPSHOT));

        // 仅推进 order-summary：inventory-snapshot 不受影响（stream_id 自然主键隔离）
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 3L));

        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(3L));
        assertThat(positions.findByStreamId(INVENTORY_SNAPSHOT))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
        assertThat(positions.findAll()).hasSize(2);
        assertThat(positions.findAll())
                .extracting(ProjectionPosition::getStreamId)
                .containsExactlyInAnyOrder(ORDER_SUMMARY, INVENTORY_SNAPSHOT);
    }

    @Test
    void save不存在则插入存在则更新_delete后不可见() {
        // save（缺行）→ MERGE INTO 插入（dispatcher 首次推进路径依赖的 upsert 语义）
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();
        ProjectionPosition saved = positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 7L));
        assertThat(saved.getStreamId()).isEqualTo(ORDER_SUMMARY);
        assertThat(saved.getNextEventNumber()).isEqualTo(7L);

        // save（已存在）→ 原位更新位置计数（同键 MERGE 不新增行）
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 9L));
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(9L));
        assertThat(positions.findAll()).hasSize(1);

        // deleteByStreamId → 行删除、findAll 归空；再删缺行静默成功
        positions.deleteByStreamId(ORDER_SUMMARY);
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();
        assertThat(positions.findAll()).isEmpty();
        positions.deleteByStreamId(ORDER_SUMMARY);
        assertThat(positions.findAll()).isEmpty();
    }

    /**
     * 直连执行 DML（建表／清表）：连接以 {@code Mono.usingWhen} 托管，完成信号即成功。
     *
     * @param sql    DML 语句（占位符 {@code $n}）
     * @param binder 占位符绑参
     * @return 完成信号
     */
    private static Mono<Void> executeUpdate(String sql, UnaryOperator<Statement> binder) {
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
