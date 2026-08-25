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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ProjectionMetrics} 与 {@link ProjectionRunner} 集成测试。
 *
 * <p>验证 metrics 回调在 runOnce 成功/失败时均被正确触发，
 * 以及 NoopProjectionMetrics 的默认空实现不会影响正常流程。
 *
 * @author PartMe.AI
 */
@DisplayName("ProjectionMetrics")
@ExtendWith(MockitoExtension.class)
class ProjectionMetricsTest {

    @Mock
    private ProjectionService projectionService;

    @Mock
    private EventChunkReader<String> chunkReader;

    /**
     * 可记录的 metrics 实现：记录所有回调调用，便于断言。
     */
    static class RecordingMetrics implements ProjectionMetrics {

        final List<String> calls = new ArrayList<>();
        String lastStreamId;
        int lastEventCount;
        long lastDurationNanos;
        long lastPositionAdvance;
        Throwable lastError;

        @Override
        public void onRunStarted(String streamId) {
            calls.add("started:" + streamId);
            this.lastStreamId = streamId;
        }

        @Override
        public void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
            calls.add("completed:" + streamId);
            this.lastStreamId = streamId;
            this.lastEventCount = eventCount;
            this.lastDurationNanos = durationNanos;
            this.lastPositionAdvance = positionAdvance;
        }

        @Override
        public void onRunFailed(String streamId, Throwable error) {
            calls.add("failed:" + streamId);
            this.lastStreamId = streamId;
            this.lastError = error;
        }
    }

    /**
     * 真实可记录的 view 实现。
     */
    static class TestView implements ProjectionView<String> {

        final String name;
        final String streamId;
        final int chunkSize;
        final List<String> eventTypes;

        TestView(String name, String streamId, int chunkSize, List<String> eventTypes) {
            this.name = name;
            this.streamId = streamId;
            this.chunkSize = chunkSize;
            this.eventTypes = eventTypes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getStreamId() {
            return Objects.nonNull(streamId) ? streamId : name;
        }

        @Override
        public String getCron() {
            return "0/5 * * * * ?";
        }

        @Override
        public int getChunkSize() {
            return chunkSize;
        }

        @Override
        public Collection<String> getEventTypes() {
            return eventTypes;
        }

        @Override
        public void handleEvents(Collection<String> events) {
            // no-op for metrics test
        }
    }

    @Nested
    @DisplayName("runOnce 成功时 metrics 回调")
    class RunOnceSuccessMetrics {

        @Test
        void runOnce_有事件_应触发started和completed回调() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, List.of("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(List.of("e1", "e2", "e3"), 3);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(chunk);

            runner.runOnce(view);

            assertThat(metrics.calls).containsExactly("started:orders", "completed:orders");
            assertThat(metrics.lastStreamId).isEqualTo("orders");
            assertThat(metrics.lastEventCount).isEqualTo(3);
            assertThat(metrics.lastPositionAdvance).isEqualTo(3);
            assertThat(metrics.lastDurationNanos).isGreaterThanOrEqualTo(0);
        }

        @Test
        void runOnce_空chunk_应触发started和completed回调且eventCount为0() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, List.of("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(5L);
            when(chunkReader.read(eq("orders"), eq(5L), eq(100), eq(List.of("created"))))
                    .thenReturn(EventChunk.empty(5));

            runner.runOnce(view);

            assertThat(metrics.calls).containsExactly("started:orders", "completed:orders");
            assertThat(metrics.lastEventCount).isEqualTo(0);
            assertThat(metrics.lastPositionAdvance).isEqualTo(0);
        }

        @Test
        void runOnce_位置未前进_应触发completed且positionAdvance为0() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, List.of("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(5L);
            // chunk 有事件但 nextEventNumber 未前进
            when(chunkReader.read(eq("orders"), eq(5L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e1"), 5));

            runner.runOnce(view);

            assertThat(metrics.calls).containsExactly("started:orders", "completed:orders");
            assertThat(metrics.lastPositionAdvance).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("runOnce 失败时 metrics 回调")
    class RunOnceFailureMetrics {

        @Test
        void runOnce_chunkReader抛异常_应触发started和failed回调() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, List.of("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            RuntimeException expectedError = new RuntimeException("chunk reader failed");
            when(chunkReader.read(any(), anyLong(), anyInt(), any()))
                    .thenThrow(expectedError);

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isSameAs(expectedError);

            assertThat(metrics.calls).containsExactly("started:orders", "failed:orders");
            assertThat(metrics.lastError).isSameAs(expectedError);
        }

        @Test
        void runOnce_view校验失败_不应触发任何metrics回调() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);

            assertThatThrownBy(() -> runner.runOnce(null))
                    .isInstanceOf(NullPointerException.class);

            // view 校验在 metrics.onRunStarted 之前，所以不应触发任何回调
            assertThat(metrics.calls).isEmpty();
        }
    }

    @Nested
    @DisplayName("NoopProjectionMetrics")
    class NoopMetricsTests {

        @Test
        void noopMetrics_单例不为null() {
            assertThat(NoopProjectionMetrics.INSTANCE).isNotNull();
        }

        @Test
        void 使用noopMetrics_不影响正常流程() {
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader,
                    NoopProjectionMetrics.INSTANCE);
            TestView view = new TestView("orders", "orders", 100, List.of("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 1);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(chunk);

            EventChunk<String> result = runner.runOnce(view);

            assertThat(result).isSameAs(chunk);
        }

        @Test
        void metrics参数为null_应默认使用noop() {
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, null);
            TestView view = new TestView("orders", "orders", 100, List.of("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(EventChunk.empty(0));

            // 不应抛异常
            runner.runOnce(view);
        }
    }
}
