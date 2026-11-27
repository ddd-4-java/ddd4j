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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link SpringJpaViewManager#getProjectionStatus(String)} 测试。
 *
 * @author PartMe.AI
 */
@DisplayName("SpringJpaViewManager")
@ExtendWith(MockitoExtension.class)
class SpringJpaViewManagerTest {

    /** \u7528\u4e8e\u9694\u79bb <code>ViewScheduler</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ViewScheduler scheduler;

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionPositionRepository</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionPositionRepository positionRepository;

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionMetrics</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionMetrics projectionMetrics;

    /** 针对 {@code getProjectionStatus} 投影状态查询行为的嵌套测试组。 */
    @Nested
    @DisplayName("getProjectionStatus")
    class GetProjectionStatusTests {

        /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>42L</code>\uff0c<code>status.lastError()</code> \u7b49\u4e8e <code>"last failure"</code>\u3002 */
        @Test
        void withMetricsIncludesLatestRunInfo() {
            Instant lastRunAt = Instant.parse("2026-10-18T00:00:00Z");
            when(positionRepository.findByStreamId("orders"))
                    .thenReturn(Optional.of(new DefaultProjectionPosition("orders", 42L)));
            when(projectionMetrics.getLastRunInfo("orders"))
                    .thenReturn(Optional.of(new ProjectionRunInfo(lastRunAt, 3, "last failure")));

            SpringJpaViewManager manager =
                    new SpringJpaViewManager(scheduler, positionRepository, projectionMetrics);
            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(42L);
            assertThat(status.lastRunAt()).isEqualTo(lastRunAt);
            assertThat(status.lastEventCount()).isEqualTo(3);
            assertThat(status.lastError()).isEqualTo("last failure");
        }

        /** \u65e0\u4f4d\u7f6e\u4ed3\u50a8\u65f6\u5e94\u8fd4\u56de\u57fa\u7ebf\u72b6\u6001\u3002 */
        @Test
        @DisplayName("无位置仓储时应返回基线状态")
        void withoutRepository_shouldReturnBaseline() {
            SpringJpaViewManager manager = new SpringJpaViewManager(scheduler);
            manager.start();

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(0L);
            assertThat(status.running()).isTrue();
            assertThat(status.lastRunAt()).isNull();
            assertThat(status.lastEventCount()).isEqualTo(0);
            assertThat(status.lastError()).isNull();
        }

        /** \u6709\u4f4d\u7f6e\u4ed3\u50a8\u4e14\u4f4d\u7f6e\u5b58\u5728\u65f6\u5e94\u8fd4\u56de\u771f\u5b9e\u4f4d\u7f6e\u3002 */
        @Test
        @DisplayName("有位置仓储且位置存在时应返回真实位置")
        void withRepositoryAndPositionExists_shouldReturnRealPosition() {
            SpringJpaViewManager manager = new SpringJpaViewManager(scheduler, positionRepository);
            manager.start();

            when(positionRepository.findByStreamId("orders"))
                    .thenReturn(Optional.of(new DefaultProjectionPosition("orders", 42L)));

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(42L);
            assertThat(status.running()).isTrue();
            assertThat(status.lastRunAt()).isNull();
            assertThat(status.lastEventCount()).isEqualTo(0);
            assertThat(status.lastError()).isNull();
        }

        /** \u6709\u4f4d\u7f6e\u4ed3\u50a8\u4f46\u4f4d\u7f6e\u4e0d\u5b58\u5728\u65f6\u5e94\u8fd4\u56de nextEventNumber=0\u3002 */
        @Test
        @DisplayName("有位置仓储但位置不存在时应返回 nextEventNumber=0")
        void withRepositoryButNoPosition_shouldReturnZeroPosition() {
            SpringJpaViewManager manager = new SpringJpaViewManager(scheduler, positionRepository);
            manager.start();

            when(positionRepository.findByStreamId("unknown"))
                    .thenReturn(Optional.empty());

            ProjectionStatus status = manager.getProjectionStatus("unknown");

            assertThat(status.streamId()).isEqualTo("unknown");
            assertThat(status.nextEventNumber()).isEqualTo(0L);
            assertThat(status.running()).isTrue();
        }

        /** \u7ba1\u7406\u5668\u505c\u6b62\u65f6 running \u5e94\u4e3a false\u3002 */
        @Test
        @DisplayName("管理器停止时 running 应为 false")
        void whenStopped_runningShouldBeFalse() {
            SpringJpaViewManager manager = new SpringJpaViewManager(scheduler, positionRepository);

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.running()).isFalse();
        }
    }
}
