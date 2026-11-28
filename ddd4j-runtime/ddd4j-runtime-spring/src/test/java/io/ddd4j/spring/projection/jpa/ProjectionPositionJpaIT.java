/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.spring.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * 投影流 ID＝handler 名（ProjectionHandler#getName 约定）。
     */
    private static final String ORDER_SUMMARY = "order-summary";

    /**
     * 供库存事件或状态恢复用例使用的快照样本。
     */
    private static final String INVENTORY_SNAPSHOT = "inventory-snapshot";

    /**
     * 测试使用或记录的多条投影位点。
     */
    @Autowired
    private JpaProjectionPositionRepository positions;

    /**
     * 用于当前读取或写入断言的测试仓储。
     */
    @Autowired
    private SpringDataProjectionPositionRepository repository;

    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 当前测试使用的框架上下文或请求作用域。
     */
    @Autowired
    private org.springframework.context.ApplicationContext context;

    /** 使用 Spring 默认扫描过滤器时，应用显式提供的适配器不应成为自动装配候选。 */
    @Test
    void defaultComponentScanDoesNotRegisterPositionAdapter() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents(JpaProjectionPositionRepository.class.getPackage().getName()))
                .extracting(candidate -> candidate.getBeanClassName())
                .doesNotContain(JpaProjectionPositionRepository.class.getName());
    }

    /**
     * 验证<code>context.getBean("jpaProjectionPositionRepository")</code> 指向同一实例 <code>positions</code>，<code>entityManager.getMetamodel().getEntities()</code> 的大小为 <code>1</code>，<code>ProjectionPositionEntity.class.getAnnotation(jakarta.persistence.Table.class).name()</code> 等于 <code>"ddd4j_projection_position"</code>。
     */
    @Test
    void preservesLegacyBeanNameAndSinglePositionProvider() {
        assertThat(context.getBean("jpaProjectionPositionRepository")).isSameAs(positions);
        assertThat(context.getBeansOfType(io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository.class))
                .containsOnlyKeys("jpaProjectionPositionRepository");
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(1);
        assertThat(entityManager.getMetamodel().entity(ProjectionPositionEntity.class).getName())
                .isEqualTo("ProjectionPositionEntity");
        assertThat(ProjectionPositionEntity.class.getAnnotation(jakarta.persistence.Table.class).name())
                .isEqualTo("ddd4j_projection_position");
    }

    /**
     * 测试执行前执行 <code>repository.deleteAll()</code> 完成资源准备。
     */
    @BeforeEach
    void cleanPositions() {
        repository.deleteAll();
    }

    /**
     * 验证<code>snapshot</code> 的大小为 <code>1</code>，<code>snapshot.clear()</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>positions.findAll()</code> 的大小为 <code>1</code>。
     */
    @Test
    void findAllReturnsAnImmutablePositionSnapshot() {
        positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));

        java.util.List<ProjectionPosition> snapshot = positions.findAll();
        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(positions.findAll()).hasSize(1);
    }

    /**
     * 验证<code>position.getNextEventNumber()</code>为零，<code>positions.incrementBy(ORDER_SUMMARY, 0L)</code> 等于 <code>5L</code>，<code>position.getNextEventNumber()</code> 等于 <code>5L</code>。
     */
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

    /**
     * 验证<code>position.getNextEventNumber()</code> 等于 <code>5L</code>，<code>position.getNextEventNumber()</code>为零。
     */
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

    /**
     * 验证<code>position.getNextEventNumber()</code>为零。
     */
    @Test
    void resetToZero_流不存在_应插入零位行() {
        // 与 core InMemoryProjectionPositionRepository 同款语义：缺行 reset 后可读到 0
        positions.resetToZero(ORDER_SUMMARY);

        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    /**
     * 验证<code>position.getNextEventNumber()</code> 等于 <code>3L</code>，<code>position.getNextEventNumber()</code>为零，<code>positions.findAll()</code> 的大小为 <code>2</code>。
     */
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

    /**
     * 验证<code>positions.findByStreamId(ORDER_SUMMARY)</code>为空，<code>saved.getStreamId()</code> 等于 <code>ORDER_SUMMARY</code>，<code>positions.findAll()</code>为空。
     */
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
