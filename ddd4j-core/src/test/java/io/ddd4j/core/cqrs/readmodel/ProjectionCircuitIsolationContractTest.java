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

/** \u901a\u8fc7 <code>Collections.singletonList()</code>\u3001<code>view()</code>\u3001<code>runner.runAllIsolated()</code> \u9a8c\u8bc1 <code>ProjectionCircuitIsolationContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class ProjectionCircuitIsolationContractTest {

    /**
     * \u6784\u9020 <code>ProjectionView&lt;String&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param fail \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>boolean</code> \u8f93\u5165
     * @param completed \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;String&gt;</code> \u7ed3\u679c
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

    /** \u9a8c\u8bc1<code>alerts.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>completed.get()</code> \u7b49\u4e8e <code>ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD</code>\uff0c<code>service.readProjectionPosition("failing")</code> \u7b49\u4e8e <code>0L</code>\u3002 */
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
