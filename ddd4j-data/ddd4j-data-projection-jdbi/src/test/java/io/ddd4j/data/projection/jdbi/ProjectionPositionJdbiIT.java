package io.ddd4j.data.projection.jdbi;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.h2.jdbcx.JdbcDataSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JdbiProjectionPositionRepository} 纯 JDBI＋H2 全量契约集成测试（Task 7.4，
 * 本地必跑轨）。
 *
 * <p>零容器：{@code Jdbi.create(JdbcDataSource)} 直连 H2 内存库，DDL 在 {@code @BeforeAll}
 * 手工执行（表结构与 -jpa/-panache 模块 {@code ProjectionPositionEntity} 同构——
 * {@code stream_id} 自然主键＋{@code next_event_number} 计数列，DDL parity），零 mock
 * ——完整验证 core SPI 语义↔手写 SQL（含 H2 MERGE INTO 原子 upsert）的组装：
 * save→自增→重读往返、resetToZero 回退（含缺行插入零位行）、跨投影流（handler 名）
 * 命名空间隔离、upsert 与删除语义。
 *
 * <p>「重启读回」模拟：仓储无任何缓存（每方法独立 Handle 直达库），断言前新建仓储
 * 实例重读——等价于进程重启后新实例的可见性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("JdbiProjectionPositionRepository 纯 JDBI + H2 全量契约 IT")
class ProjectionPositionJdbiIT {

    /** 投影流 ID＝handler 名（ProjectionHandler#getName 约定）。 */
    private static final String ORDER_SUMMARY = "order-summary";

    private static final String INVENTORY_SNAPSHOT = "inventory-snapshot";

    /**
     * 与 -jpa/-panache 模块同构的 DDL（列集 parity：{@code stream_id} VARCHAR(250)
     * 自然主键＋{@code next_event_number} BIGINT 非空计数）。
     */
    private static final String DDL = """
            create table if not exists ddd4j_projection_position (
                stream_id varchar(250) not null primary key,
                next_event_number bigint not null
            )""";

    private static Jdbi jdbi;

    private JdbiProjectionPositionRepository positions;

    @BeforeAll
    static void setUpDatabase() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ddd4j_projection_jdbi_it;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbi = Jdbi.create(dataSource);
        jdbi.useHandle(handle -> handle.execute(DDL));
    }

    @BeforeEach
    void cleanPositions() {
        jdbi.useHandle(handle -> handle.execute("delete from ddd4j_projection_position"));
        // 每用例新建仓储实例：受测代码零状态，亦证明断言不依赖任何实例内缓存
        positions = new JdbiProjectionPositionRepository(jdbi);
    }

    @Test
    void saveIncrReload_完整循环_应跨仓储实例读回最终位置() {
        // 先落零位行（dispatcher 冷启动路径）
        ProjectionPosition saved = positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));
        assertThat(saved.getStreamId()).isEqualTo(ORDER_SUMMARY);
        assertThat(saved.getNextEventNumber()).isZero();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());

        // 逐事件推进到 5（每次一条 MERGE INTO 原子 upsert）
        ProjectionPosition current = saved;
        for (long i = 0; i < 5; i++) {
            current = positions.save(current.withNextEventNumber(current.getNextEventNumber() + 1));
        }
        assertThat(current.getNextEventNumber()).isEqualTo(5L);

        // 重启读回：新建仓储实例从库重读，位置仍为 5（持久性，非实例态内存值）
        assertThat(new JdbiProjectionPositionRepository(jdbi).findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> {
                    assertThat(position.getStreamId()).isEqualTo(ORDER_SUMMARY);
                    assertThat(position.getNextEventNumber()).isEqualTo(5L);
                });
    }

    @Test
    void resetToZero_推进后重置_应回退到0() {
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 5L));
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(5L));

        positions.resetToZero(ORDER_SUMMARY);

        assertThat(new JdbiProjectionPositionRepository(jdbi).findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    @Test
    void resetToZero_流不存在_应插入零位行() {
        // 与 core InMemoryProjectionPositionRepository 同款语义：缺行 reset 后可读到 0
        positions.resetToZero(ORDER_SUMMARY);

        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    @Test
    void 两个handler命名空间_分别save后独立推进_互不串扰() {
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
}
