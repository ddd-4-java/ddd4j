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
     * \u9a8c\u8bc1<code>QuarkusJpaViewManager.DEFAULT_CRON</code> \u7b49\u4e8e <code>"*&#47;30 * * * * ?"</code>\uff0c<code>QuarkusJpaViewManager.CRON_KEY</code> \u7b49\u4e8e <code>"ddd4j.quarkus.cqrs.projection.cron"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void usesThirtySecondDefaultCron() throws Exception {
        assertEquals("*/30 * * * * ?", QuarkusJpaViewManager.DEFAULT_CRON);
        assertEquals("ddd4j.quarkus.cqrs.projection.cron", QuarkusJpaViewManager.CRON_KEY);
    }

    /** \u9a8c\u8bc1<code>service.streams</code> \u7b49\u4e8e <code>Collections.emptyList()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>service.streams</code> \u7b49\u4e8e <code>Arrays.asList("failed", "next")</code>\u3002 */
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
     * \u901a\u8fc7 <code>mock()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>mock(Instance.class)</code>
     */
    @SuppressWarnings("unchecked")
    private Instance<ProjectionPositionRepository> repositoryInstance() {
        return mock(Instance.class);
    }

    /** \u5b9e\u73b0 <code>QuarkusJpaProjectionService</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>pullAndApply()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class RecordingProjectionService extends QuarkusJpaProjectionService {

        /** \u5f53\u524d\u6d4b\u8bd5\u8bfb\u53d6\u3001\u8ba4\u9886\u6216\u53d1\u5e03\u6d88\u606f\u4f7f\u7528\u7684\u6d41\u96c6\u5408\u3002 */
        private final List<String> streams = new ArrayList<>();
        /** \u63a7\u5236\u6d4b\u8bd5\u66ff\u8eab\u5728\u54ea\u4e2a\u8c03\u7528\u6761\u4ef6\u4e0b\u629b\u51fa\u5f02\u5e38\u3002 */
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
