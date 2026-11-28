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
package io.ddd4j.data.projection.panache;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.scheduler.Scheduler;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuarkusProjectionPositionRepository} H2 集成测试（Task 7.3，本地必跑轨，Docker 无关）。
 *
 * <p>真实 Quarkus 容器（@QuarkusTest）＋真实 H2 内存库（{@code application.properties}，
 * MODE=PostgreSQL，devservices 关闭）＋真实 Panache 持久化原语，零 mock——完整验证
 * core SPI 语义↔Panache 持久化原语的组装：save/推进/读回循环、resetToZero 回退与
 * 缺行插入零位语义、upsert 与删除、跨投影流（handler 名）命名空间隔离。
 * 契约面与 {@code ProjectionPositionJpaIT}（-jpa 模块）对齐（incrementBy 为 -jpa 模块
 * 扩展，本模块无此方法，推进以 SPI 的 save + withNextEventNumber 表达）。
 *
 * <p>「重启读回」模拟：每次仓储调用即一个已提交事务（方法级 @Transactional）；
 * 读回前再 {@link EntityManager#clear()} 清空一级缓存，强制从库重载——等价于进程
 * 重启后新持久化上下文的可见性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("QuarkusProjectionPositionRepository H2 IT")
@QuarkusTest
class QuarkusProjectionPositionRepositoryIT {

    /** 缺省配置下检查投影运行状态。 */
    @Inject
    ViewManager manager;

    /** 用真实调度记录核对旧应用默认的定时任务。 */
    @Inject
    Scheduler scheduler;

    /** 未指定模式时不启动投影，也不登记任何定时任务。 */
    @Test
    void defaultModeLeavesProjectionStopped() {
        assertThat(manager.isRunning()).isFalse();
        assertThat(scheduler.getScheduledJobs()).isEmpty();
    }

    /**
     * 投影流 ID＝handler 名（ProjectionHandler#getName 约定）。
     */
    private static final String ORDER_SUMMARY = "order-summary";

    /** \u4f9b\u5e93\u5b58\u4e8b\u4ef6\u6216\u72b6\u6001\u6062\u590d\u7528\u4f8b\u4f7f\u7528\u7684\u5feb\u7167\u6837\u672c\u3002 */
    private static final String INVENTORY_SNAPSHOT = "inventory-snapshot";

    /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
    @Inject
    QuarkusProjectionPositionRepository positions;

    /** \u4e3a\u96c6\u6210\u6d4b\u8bd5\u5e94\u7528\u542f\u52a8\u63d0\u4f9b\u7684\u6846\u67b6\u914d\u7f6e\u3002 */
    @Inject
    ProjectionPanacheItCdiConfig itConfig;

    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    @PersistenceContext
    EntityManager entityManager;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>itConfig.clearPositions()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void cleanPositions() {
        itConfig.clearPositions();
    }

    /** \u9a8c\u8bc1<code>snapshot</code>\u4e3a\u7a7a\uff0c<code>positions.findAll()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void findAll_返回可修改的独立结果快照() {
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 7L));

        List<ProjectionPosition> snapshot = positions.findAll();
        snapshot.clear();

        assertThat(snapshot).isEmpty();
        assertThat(positions.findAll()).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>position.getNextEventNumber()</code>\u4e3a\u96f6\uff0c<code>position.getStreamId()</code> \u7b49\u4e8e <code>ORDER_SUMMARY</code>\uff0c<code>position.getNextEventNumber()</code> \u7b49\u4e8e <code>5L</code>\u3002 */
    @Test
    void saveAdvanceReload_完整循环_应跨持久化上下文读回最终位置() {
        // 先落零位行（dispatcher 冷启动路径）
        positions.save(DefaultProjectionPosition.zero(ORDER_SUMMARY));
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());

        // 逐事件推进到 5（dispatcher 实际用法：内存推进 + save 落库，SPI 的 save 即
        // upsert 原位更新；每步 save 各自事务提交后库中位置随之前进）
        ProjectionPosition current = DefaultProjectionPosition.zero(ORDER_SUMMARY);
        for (int i = 0; i < 5; i++) {
            current = current.withNextEventNumber(current.getNextEventNumber() + 1L);
            positions.save(current);
        }

        // 重启读回：清空一级缓存后从库重载，位置仍为 5（持久性，非托管态内存值）
        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> {
                    assertThat(position.getStreamId()).isEqualTo(ORDER_SUMMARY);
                    assertThat(position.getNextEventNumber()).isEqualTo(5L);
                });
    }

    /** \u9a8c\u8bc1<code>position.getNextEventNumber()</code> \u7b49\u4e8e <code>5L</code>\uff0c<code>position.getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
    @Test
    void resetToZero_推进后重置_应回退到0() {
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 5L));
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(5L));

        positions.resetToZero(ORDER_SUMMARY);

        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    /** \u9a8c\u8bc1<code>positions.findByStreamId(ORDER_SUMMARY)</code>\u4e3a\u7a7a\uff0c<code>position.getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
    @Test
    void resetToZero_流不存在_应插入零位行() {
        // 与 core InMemoryProjectionPositionRepository 同款语义：缺行 reset 后可读到 0
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();

        positions.resetToZero(ORDER_SUMMARY);

        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
    }

    /** \u9a8c\u8bc1<code>positions.findByStreamId(ORDER_SUMMARY)</code>\u4e3a\u7a7a\uff0c<code>saved.getStreamId()</code> \u7b49\u4e8e <code>ORDER_SUMMARY</code>\uff0c<code>positions.findAll()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\u3002 */
    @Test
    void save不存在则插入存在则更新_delete后不可见_且命名空间隔离() {
        // save（缺行）→ 插入（dispatcher 首次推进路径依赖的 upsert 语义）
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();
        ProjectionPosition saved = positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 7L));
        assertThat(saved.getStreamId()).isEqualTo(ORDER_SUMMARY);
        assertThat(saved.getNextEventNumber()).isEqualTo(7L);

        // save（已存在）→ 原位更新位置计数
        positions.save(new DefaultProjectionPosition(ORDER_SUMMARY, 9L));
        // 第二投影流独立落行（stream_id 自然主键隔离命名空间）
        positions.save(DefaultProjectionPosition.zero(INVENTORY_SNAPSHOT));

        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(9L));
        assertThat(positions.findByStreamId(INVENTORY_SNAPSHOT))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isZero());
        assertThat(positions.findAll()).hasSize(2);
        assertThat(positions.findAll())
                .extracting(ProjectionPosition::getStreamId)
                .containsExactlyInAnyOrder(ORDER_SUMMARY, INVENTORY_SNAPSHOT);

        // deleteByStreamId → 行删除、findAll 归空（另一流不受影响）
        positions.deleteByStreamId(ORDER_SUMMARY);
        entityManager.clear();
        assertThat(positions.findByStreamId(ORDER_SUMMARY)).isEmpty();
        assertThat(positions.findAll())
                .extracting(ProjectionPosition::getStreamId)
                .containsExactly(INVENTORY_SNAPSHOT);
    }
}
