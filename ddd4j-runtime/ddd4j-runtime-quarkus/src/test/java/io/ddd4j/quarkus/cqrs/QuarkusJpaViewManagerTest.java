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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link QuarkusJpaViewManager#getProjectionStatus(String)} 测试。
 *
 * <p>由于 Quarkus CDI 容器不在单元测试中启动，通过反射注入 mock Instance 模拟 CDI 行为。
 *
 * @author PartMe.AI
 */
@DisplayName("QuarkusJpaViewManager")
@ExtendWith(MockitoExtension.class)
class QuarkusJpaViewManagerTest {

    @Mock
    private Instance<ProjectionPositionRepository> positionRepositories;

    @Mock
    private ProjectionPositionRepository positionRepository;

    /**
     * 通过反射设置 CDI Instance 字段。
     */
    private QuarkusJpaViewManager createManager(Instance<ProjectionPositionRepository> repos) throws Exception {
        QuarkusJpaViewManager manager = new QuarkusJpaViewManager();
        Field field = QuarkusJpaViewManager.class.getDeclaredField("positionRepositories");
        field.setAccessible(true);
        field.set(manager, repos);
        return manager;
    }

    @Nested
    @DisplayName("getProjectionStatus")
    class GetProjectionStatusTests {

        @Test
        @DisplayName("CDI 仓储不可用时应返回基线状态")
        void whenUnsatisfied_shouldReturnBaseline() throws Exception {
            when(positionRepositories.isUnsatisfied()).thenReturn(true);
            QuarkusJpaViewManager manager = createManager(positionRepositories);
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
        @DisplayName("CDI 仓储可用且位置存在时应返回真实位置")
        void whenAvailableAndPositionExists_shouldReturnRealPosition() throws Exception {
            when(positionRepositories.isUnsatisfied()).thenReturn(false);
            when(positionRepositories.get()).thenReturn(positionRepository);
            when(positionRepository.findByStreamId("orders"))
                    .thenReturn(Optional.of(new DefaultProjectionPosition("orders", 42L)));

            QuarkusJpaViewManager manager = createManager(positionRepositories);
            manager.start();

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(42L);
            assertThat(status.running()).isTrue();
        }

        @Test
        @DisplayName("CDI 仓储可用但位置不存在时应返回 nextEventNumber=0")
        void whenAvailableButNoPosition_shouldReturnZeroPosition() throws Exception {
            when(positionRepositories.isUnsatisfied()).thenReturn(false);
            when(positionRepositories.get()).thenReturn(positionRepository);
            when(positionRepository.findByStreamId("unknown"))
                    .thenReturn(Optional.empty());

            QuarkusJpaViewManager manager = createManager(positionRepositories);
            manager.start();

            ProjectionStatus status = manager.getProjectionStatus("unknown");

            assertThat(status.streamId()).isEqualTo("unknown");
            assertThat(status.nextEventNumber()).isEqualTo(0L);
        }

        @Test
        @DisplayName("管理器停止时 running 应为 false")
        void whenStopped_runningShouldBeFalse() throws Exception {
            when(positionRepositories.isUnsatisfied()).thenReturn(true);
            QuarkusJpaViewManager manager = createManager(positionRepositories);
            // 未调用 start()，running 为 false

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.running()).isFalse();
        }
    }
}
