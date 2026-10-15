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

class ProjectionCircuitIsolationContractTest {

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
}
