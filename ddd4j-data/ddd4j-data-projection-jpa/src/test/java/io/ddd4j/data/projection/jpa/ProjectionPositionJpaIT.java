package io.ddd4j.data.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JpaProjectionPositionRepository} H2 集成测试（Task 7.2，本地必跑轨）。
 *
 * <p>真实 Spring 容器＋真实 H2 内存库（MODE=PostgreSQL，{@code application-test.yml}），
 * 零 mock——完整验证 core SPI 语义↔JPA 持久化原语的组装：save/读回往返、数据库端原子
 * 自增、resetToZero 回退、跨投影流（handler 名）命名空间隔离、upsert 与删除语义。
 *
 * <p>「重启读回」模拟：测试方法不带 {@code @Transactional}，每次仓储调用即一个已提交
 * 事务；读回前再 {@link EntityManager#clear()} 清空一级缓存，强制从库重载——
 * 等价于进程重启后新持久化上下文的可见性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("JpaProjectionPositionRepository H2 IT")
@SpringBootTest(classes = TestApp.class)
@ActiveProfiles("test")
class ProjectionPositionJpaIT {

    /** 投影流 ID＝handler 名（ProjectionHandler#getName 约定）。 */
    private static final String ORDER_SUMMARY = "order-summary";

    private static final String INVENTORY_SNAPSHOT = "inventory-snapshot";

    @Autowired
    private JpaProjectionPositionRepository positions;

    @Autowired
    private SpringDataProjectionPositionRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void cleanPositions() {
        repository.deleteAll();
    }

    @Test
    void saveIncrReload_完整循环_应跨持久化上下文读回最终位置() {
        // 先落零位行（dispatcher 冷启动路径）
        positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());

        // 逐事件自增到 5（每次一条数据库端原子 UPDATE）
        for (int i = 0; i < 5; i++) {
            positions.incrementBy(ORDER_SUMMARY, 1L);
        }
        assertThat(positions.incrementBy(ORDER_SUMMARY, 0L)).isEqualTo(5L);

        // 重启读回：清空一级缓存后从库重载，位置仍为 5（持久性，非托管态内存值）
        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> {
                    assertThat(position.getStreamId()).isEqualTo(ORDER_SUMMARY);
                    assertThat(position.getNextEventNumber()).isEqualTo(5L);
                });
    }

    @Test
    void resetToZero_推进后重置_应回退到0() {
        positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));
        positions.incrementBy(ORDER_SUMMARY, 5L);
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(5L));

        positions.resetToZero(ORDER_SUMMARY);

        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    @Test
    void resetToZero_流不存在_应插入零位行() {
        // 与 core InMemoryProjectionPositionRepository 同款语义：缺行 reset 后可读到 0
        positions.resetToZero(ORDER_SUMMARY);

        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    @Test
    void 两个handler命名空间_分别save后独立递增_互不串扰() {
        positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));
        positions.save(DefaultProjectionPosition.zero(INVENTORY_SNAPSHOT));

        // 仅推进 order-summary：inventory-snapshot 不受影响（stream_id 自然主键隔离）
        positions.incrementBy(ORDER_SUMMARY, 3L);

        entityManager.clear();
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
        // save（缺行）→ 插入（dispatcher 首次推进路径依赖的 upsert 语义）
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();
        ProjectionPosition saved = positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 7L));
        assertThat(saved.getStreamId()).isEqualTo(ORDER_SUMMARY);
        assertThat(saved.getNextEventNumber()).isEqualTo(7L);

        // save（已存在）→ 原位更新位置计数
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 9L));
        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(9L));

        // deleteByStreamId → 行删除、findAll 归空
        positions.deleteByStreamId(ORDER_SUMMARY);
        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();
        assertThat(positions.findAll()).isEmpty();
    }
}
