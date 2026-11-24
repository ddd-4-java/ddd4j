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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpringJpaViewManager#getProjectionStatus(String)} 测试。
 *
 * @author PartMe.AI
 */
@DisplayName("SpringJpaViewManager")
@ExtendWith(MockitoExtension.class)
class SpringJpaViewManagerTest {

    @Mock
    private ViewScheduler scheduler;

    @Mock
    private ProjectionPositionRepository positionRepository;

    @Mock
    private ProjectionMetrics projectionMetrics;

    @Test
    void contextCloseCancelsManualSchedulesAndKeepsSubclassTrigger() {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger triggered = new java.util.concurrent.atomic.AtomicInteger();
        ViewScheduler ownedScheduler = (name, cron, task) -> new ViewScheduler.ViewScheduleHandle() {
            public void cancel() { cancelled.set(true); }
            public boolean isActive() { return !cancelled.get(); }
        };
        SpringJpaViewManager manager = new SpringJpaViewManager(ownedScheduler) {
            @Override
            public void triggerOnce() { triggered.incrementAndGet(); }
        };
        try (org.springframework.context.annotation.AnnotationConfigApplicationContext context =
                     new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean("manual", SpringJpaViewManager.class, () -> manager);
            context.refresh();
            assertThat(manager.isRunning()).isTrue();
            manager.schedule("manual", "0 0 0 * * *", manager::triggerOnce);
            manager.triggerOnce();
            assertThat(triggered.get()).isEqualTo(1);
        }
        assertThat(manager.isRunning()).isFalse();
        assertThat(cancelled.get()).isTrue();
    }


    @Test
    void stopCancelsHandleRegisteredBeforeStart() {
        ViewScheduler.ViewScheduleHandle handle = mock(ViewScheduler.ViewScheduleHandle.class);
        Runnable task = () -> { };
        when(scheduler.schedule("early", "0 0 0 * * *", task)).thenReturn(handle);
        SpringJpaViewManager manager = new SpringJpaViewManager(scheduler);
        manager.schedule("early", "0 0 0 * * *", task);

        manager.stop();
        manager.stop();

        verify(handle).cancel();
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void stopCleansOtherHandlesAndCanRetryFailedCancellation() {
        ViewScheduler.ViewScheduleHandle bad = mock(ViewScheduler.ViewScheduleHandle.class);
        ViewScheduler.ViewScheduleHandle good = mock(ViewScheduler.ViewScheduleHandle.class);
        Runnable task = () -> { };
        LinkageError failure = new LinkageError("schedule transport unavailable");
        doThrow(failure).doNothing().when(bad).cancel();

        SpringJpaViewManager manager = new SpringJpaViewManager(scheduler);
        manager.start();
        when(scheduler.schedule("a", "0 0 0 * * *", task)).thenReturn(bad);
        when(scheduler.schedule("b", "0 0 0 * * *", task)).thenReturn(good);
        manager.schedule("a", "0 0 0 * * *", task);
        manager.schedule("b", "0 0 0 * * *", task);

        assertThatThrownBy(manager::stop).isSameAs(failure);
        assertThat(manager.isRunning()).isFalse();
        verify(good).cancel();

        manager.stop();
        manager.stop();

        verify(bad, times(2)).cancel();
        verify(good).cancel();
    }

    /** 针对 {@code getProjectionStatus} 投影状态查询行为的嵌套测试组。 */
    @Nested
    @DisplayName("getProjectionStatus")
    class GetProjectionStatusTests {

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

        @Test
        @DisplayName("管理器停止时 running 应为 false")
        void whenStopped_runningShouldBeFalse() {
            SpringJpaViewManager manager = new SpringJpaViewManager(scheduler, positionRepository);

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.running()).isFalse();
        }
    }
}
