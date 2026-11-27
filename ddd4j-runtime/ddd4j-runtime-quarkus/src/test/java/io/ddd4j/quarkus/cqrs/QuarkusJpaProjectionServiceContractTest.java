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

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * 三线共同的 Quarkus 投影拉取契约。
 */
class QuarkusJpaProjectionServiceContractTest {

    /**
     * 验证<code>QuarkusJpaViewManager.DEFAULT_CRON</code> 等于 <code>"*&#47;30 * * * * ?"</code>，<code>QuarkusJpaViewManager.CRON_KEY</code> 等于 <code>"ddd4j.quarkus.cqrs.projection.cron"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void usesThirtySecondDefaultCron() throws Exception {
        assertEquals("*/30 * * * * ?", QuarkusJpaViewManager.DEFAULT_CRON);
        assertEquals("ddd4j.quarkus.cqrs.projection.cron", QuarkusJpaViewManager.CRON_KEY);
    }

    /**
     * 验证<code>service.streams</code> 等于 <code>Collections.emptyList()</code>。
     */
    @Test
    void skipsMissingOrEmptyRepository() {
        RecordingProjectionService service = new RecordingProjectionService();
        Instance<ProjectionPositionRepository> instance = repositoryInstance();
        service.projectionPositionRepository = instance;
        when(instance.isUnsatisfied()).thenReturn(true);

        service.runOnce();

        verify(instance, never()).get();
        assertEquals(Collections.emptyList(), service.streams);

        ProjectionPositionRepository repository = mock(ProjectionPositionRepository.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(repository);
        when(repository.findAll()).thenReturn(Collections.emptyList());

        service.runOnce();

        assertEquals(Collections.emptyList(), service.streams);
    }

    /**
     * 验证<code>service.streams</code> 等于 <code>Arrays.asList("failed", "next")</code>。
     */
    @Test
    void failureInOneStreamDoesNotSkipTheNext() {
        RecordingProjectionService service = new RecordingProjectionService();
        service.failOn = "failed";
        Instance<ProjectionPositionRepository> instance = repositoryInstance();
        ProjectionPositionRepository repository = mock(ProjectionPositionRepository.class);
        ProjectionPosition first = mock(ProjectionPosition.class);
        ProjectionPosition second = mock(ProjectionPosition.class);
        when(instance.get()).thenReturn(repository);
        when(repository.findAll()).thenReturn(Arrays.asList(first, second));
        when(first.getStreamId()).thenReturn("failed");
        when(second.getStreamId()).thenReturn("next");
        service.projectionPositionRepository = instance;

        service.runOnce();

        assertEquals(Arrays.asList("failed", "next"), service.streams);
    }

    /**
     * 通过 <code>mock()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>mock(Instance.class)</code>
     */
    @SuppressWarnings("unchecked")
    private Instance<ProjectionPositionRepository> repositoryInstance() {
        return mock(Instance.class);
    }

    /**
     * 实现 <code>QuarkusJpaProjectionService</code>，供测试调用 <code>pullAndApply()</code> 并观察结果。
     */
    private static final class RecordingProjectionService extends QuarkusJpaProjectionService {

        /**
         * 当前测试读取、认领或发布消息使用的流集合。
         */
        private final List<String> streams = new ArrayList<>();
        /**
         * 控制测试替身在哪个调用条件下抛出异常。
         */
        private String failOn;

        /**
         * 拉取投影尚未处理的事件并更新读模型。
         *
         * <p>测试夹具会调用 <code>streams.add(streamId)</code>、<code>streamId.equals(failOn)</code>。</p>
         *
         * @param streamId 投影流标识
         */
        @Override
        protected void pullAndApply(String streamId) {
            streams.add(streamId);
            if (streamId.equals(failOn)) {
                throw new IllegalStateException("simulated projection failure");
            }
        }
    }
}
