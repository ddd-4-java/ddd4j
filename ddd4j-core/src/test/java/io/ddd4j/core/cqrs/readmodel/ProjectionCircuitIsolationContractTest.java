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
package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>Collections.singletonList()</code>、<code>view()</code>、<code>runner.runAllIsolated()</code> 验证 <code>ProjectionCircuitIsolationContract</code> 的运行行为。
 */
class ProjectionCircuitIsolationContractTest {

    /**
     * 构造 <code>ProjectionView&lt;String&gt;</code> 作为当前测试的输入或结果。
     *
     * @param name      当前测试指定的对象或属性名称
     * @param fail      用于构造本次返回结果的 <code>boolean</code> 输入
     * @param completed 用于构造本次返回结果的 <code>AtomicInteger</code> 输入
     * @return 提供给后续契约断言的 <code>ProjectionView&lt;String&gt;</code> 结果
     */
    private static ProjectionView<String> view(String name, boolean fail, AtomicInteger completed) {
        return new ProjectionView<String>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCron() {
                return "* * * * *";
            }

            @Override
            public Collection<String> getEventTypes() {
                return Collections.emptyList();
            }

            @Override
            public void handleEvents(Collection<String> events) {
                if (fail) {
                    throw new IllegalStateException("projection failed");
                }
                completed.incrementAndGet();
            }
        };
    }

    /**
     * 验证<code>alerts.get()</code> 等于 <code>1</code>，<code>completed.get()</code> 等于 <code>ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD</code>，<code>service.readProjectionPosition("failing")</code> 等于 <code>0L</code>。
     */
    @Test
    void circuitCallbackFailureMustNotStopOtherViews() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger alerts = new AtomicInteger();
        EventChunkReader<String> reader = (streamId, from, size, types) ->
                new EventChunk<String>(Collections.singletonList(streamId), from + 1L);
        ProjectionMetrics metrics = new ProjectionMetrics() {
            @Override
            public void onCircuitOpened(String viewName, int consecutiveFailures) {
                alerts.incrementAndGet();
                throw new IllegalStateException("alert transport unavailable");
            }
        };
        ProjectionRunner<String> runner = new ProjectionRunner<String>(service, reader, metrics);
        ProjectionView<String> failing = view("failing", true, completed);
        ProjectionView<String> succeeding = view("succeeding", false, completed);

        for (int i = 0; i < ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD; i++) {
            assertDoesNotThrow(() -> runner.runAllIsolated(Arrays.asList(failing, succeeding)));
        }

        assertEquals(1, alerts.get());
        assertEquals(ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD, completed.get());
        assertEquals(ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD,
                service.readProjectionPosition("succeeding"));
        assertEquals(0L, service.readProjectionPosition("failing"));
    }
}
