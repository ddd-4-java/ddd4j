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
import javax.enterprise.inject.Instance;
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

    /** \u7528\u4e8e\u9694\u79bb <code>Instance&lt;ProjectionPositionRepository&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Instance<ProjectionPositionRepository> positionRepositories;

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionPositionRepository</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionPositionRepository positionRepository;

    /**
     * \u901a\u8fc7\u53cd\u5c04\u8bbe\u7f6e CDI Instance \u5b57\u6bb5\u3002
     *
     * @param repos \u6d4b\u8bd5\u4f7f\u7528\u7684\u6295\u5f71\u4f4d\u70b9\u4ed3\u50a8\u96c6\u5408
     * @return \u4f7f\u7528\u6d4b\u8bd5\u4ed3\u50a8\u521b\u5efa\u7684 Quarkus \u89c6\u56fe\u7ba1\u7406\u5668
     * @throws Exception \u65e0\u6cd5\u53cd\u5c04\u6ce8\u5165\u4ed3\u50a8
     */
    private QuarkusJpaViewManager createManager(Instance<ProjectionPositionRepository> repos) throws Exception {
        QuarkusJpaViewManager manager = new QuarkusJpaViewManager();
        Field field = QuarkusJpaViewManager.class.getDeclaredField("positionRepositories");
        field.setAccessible(true);
        field.set(manager, repos);
        return manager;
    }

    /** getProjectionStatus \u5404\u5206\u652f\u884c\u4e3a\u7684\u6821\u9a8c\u7528\u4f8b\u7ec4\u3002 */
    @Nested
    @DisplayName("getProjectionStatus")
    class GetProjectionStatusTests {

        /**
         * \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.lastError()</code>\u4e3a\u7a7a\u3002
         * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
         */
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

        /**
         * \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.running()</code>\u4e3a\u771f\u3002
         * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
         */
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

        /**
         * \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"unknown"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\u3002
         * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
         */
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

        /**
         * \u9a8c\u8bc1<code>status.running()</code>\u4e3a\u5047\u3002
         * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
         */
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
