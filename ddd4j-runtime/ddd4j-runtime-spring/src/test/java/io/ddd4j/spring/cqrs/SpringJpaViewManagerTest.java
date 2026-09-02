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
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private ViewScheduler scheduler;

    @Mock
    private ProjectionPositionRepository positionRepository;

    @Nested
    @DisplayName("getProjectionStatus")
    class GetProjectionStatusTests {

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
