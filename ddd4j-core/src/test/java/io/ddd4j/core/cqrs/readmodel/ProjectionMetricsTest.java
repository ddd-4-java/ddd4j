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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    /**
     * 用于隔离 <code>ProjectionService</code> 调用并记录交互的测试替身。
     */
    @Mock
    private ProjectionService projectionService;

    /**
     * 用于隔离 <code>EventChunkReader&lt;String&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    private EventChunkReader<String> chunkReader;

    /**
     * 可记录的 metrics 实现：记录所有回调调用，便于断言。
     */
    static class RecordingMetrics implements ProjectionMetrics {

        /**
         * 记录被测入口实际触发的调用。
         */
        final List<String> calls = new ArrayList<>();
        /**
         * 最近一次回调收到的投影流标识。
         */
        String lastStreamId;
        /**
         * 最近一次记录的处理事件数。
         */
        int lastEventCount;
        /**
         * 最近一次收到的投影处理耗时，单位为纳秒。
         */
        long lastDurationNanos;
        /**
         * 最近一次投影通知中观察到的位点推进量。
         */
        long lastPositionAdvance;
        /**
         * 最近一次观察到的被测操作错误信息。
         */
        Throwable lastError;

        /**
         * 投影运行开始回调。
         *
         * <p>测试夹具会调用 <code>calls.add("started:" + streamId)</code>。</p>
         *
         * @param streamId 投影流 ID
         */
        @Override
        public void onRunStarted(String streamId) {
            calls.add("started:" + streamId);
            this.lastStreamId = streamId;
        }

        /**
         * 投影运行成功回调。
         *
         * <p>测试夹具会调用 <code>calls.add("completed:" + streamId)</code>。</p>
         *
         * @param streamId        投影流 ID
         * @param eventCount      本次处理的事件数量
         * @param durationNanos   本次运行耗时（纳秒）
         * @param positionAdvance 本次位置推进量（nextEventNumber - previousEventNumber）
         */
        @Override
        public void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
            calls.add("completed:" + streamId);
            this.lastStreamId = streamId;
            this.lastEventCount = eventCount;
            this.lastDurationNanos = durationNanos;
            this.lastPositionAdvance = positionAdvance;
        }

        /**
         * 投影运行失败回调。
         *
         * <p>测试夹具会调用 <code>calls.add("failed:" + streamId)</code>。</p>
         *
         * @param streamId 投影流 ID
         * @param error    导致失败的异常
         */
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

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        final String name;
        /**
         * 投影任务使用的事件流标识。
         */
        final String streamId;
        /**
         * 分批读取事件时配置的单批大小。
         */
        final int chunkSize;
        /**
         * 夹具向投影运行器声明订阅的事件类型。
         */
        final List<String> eventTypes;

        /**
         * 用给定值初始化 <code>TestView</code> 实例。
         *
         * @param name       <code>name</code> 字段
         * @param streamId   投影任务使用的事件流标识
         * @param chunkSize  <code>chunkSize</code> 字段
         * @param eventTypes <code>eventTypes</code> 字段
         */
        TestView(String name, String streamId, int chunkSize, List<String> eventTypes) {
            this.name = name;
            this.streamId = streamId;
            this.chunkSize = chunkSize;
            this.eventTypes = eventTypes;
        }

        /**
         * 返回当前配置、视图或处理器的名称。
         *
         * @return 实际返回<code>name</code>，供调用方取得当前值
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * 返回当前投影绑定的事件流标识。
         *
         * @return 实际返回<code>Objects.nonNull(streamId) ? streamId : name</code>，供调用方取得当前值
         */
        @Override
        public String getStreamId() {
            return Objects.nonNull(streamId) ? streamId : name;
        }

        /**
         * 返回投影视图配置的调度表达式。
         *
         * @return 实际返回<code>"0/5 * * * * ?"</code>，供调用方取得当前值
         */
        @Override
        public String getCron() {
            return "0/5 * * * * ?";
        }

        /**
         * 返回投影单次读取事件的最大条数。
         *
         * @return 实际返回<code>chunkSize</code>，供调用方取得当前值
         */
        @Override
        public int getChunkSize() {
            return chunkSize;
        }

        /**
         * 返回投影或处理器关心的事件类型。
         *
         * @return 实际返回<code>eventTypes</code>，供调用方取得当前值
         */
        @Override
        public Collection<String> getEventTypes() {
            return eventTypes;
        }

        /**
         * 提供测试替身回调，调用时保持夹具状态。
         *
         * @param events 事件列表
         */
        @Override
        public void handleEvents(Collection<String> events) {
            // no-op for metrics test
        }
    }

    /**
     * 覆盖 <code>runOnce 成功时 metrics 回调</code> 场景及其预期行为。
     */
    @Nested
    @DisplayName("runOnce 成功时 metrics 回调")
    class RunOnceSuccessMetrics {

        /**
         * 验证<code>metrics.calls</code> 按顺序仅包含 <code>"started:orders"</code>、<code>"completed:orders"</code>，<code>metrics.lastStreamId</code> 等于 <code>"orders"</code>，<code>metrics.lastDurationNanos</code> 大于或等于 <code>0</code>。
         */
        @Test
        void runOnce_有事件_应触发started和completed回调() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, Arrays.asList("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList("e1", "e2", "e3"), 3);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(chunk);

            runner.runOnce(view);

            assertThat(metrics.calls).containsExactly("started:orders", "completed:orders");
            assertThat(metrics.lastStreamId).isEqualTo("orders");
            assertThat(metrics.lastEventCount).isEqualTo(3);
            assertThat(metrics.lastPositionAdvance).isEqualTo(3);
            assertThat(metrics.lastDurationNanos).isGreaterThanOrEqualTo(0);
        }

        /**
         * 验证<code>metrics.calls</code> 按顺序仅包含 <code>"started:orders"</code>、<code>"completed:orders"</code>，<code>metrics.lastEventCount</code> 等于 <code>0</code>，<code>metrics.lastPositionAdvance</code> 等于 <code>0</code>。
         */
        @Test
        void runOnce_空chunk_应触发started和completed回调且eventCount为0() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, Arrays.asList("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(5L);
            when(chunkReader.read(eq("orders"), eq(5L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(5));

            runner.runOnce(view);

            assertThat(metrics.calls).containsExactly("started:orders", "completed:orders");
            assertThat(metrics.lastEventCount).isEqualTo(0);
            assertThat(metrics.lastPositionAdvance).isEqualTo(0);
        }

        /**
         * 验证<code>metrics.calls</code> 按顺序仅包含 <code>"started:orders"</code>、<code>"completed:orders"</code>，<code>metrics.lastPositionAdvance</code> 等于 <code>0</code>。
         */
        @Test
        void runOnce_位置未前进_应触发completed且positionAdvance为0() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, Arrays.asList("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(5L);
            // chunk 有事件但 nextEventNumber 未前进
            when(chunkReader.read(eq("orders"), eq(5L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e1"), 5));

            runner.runOnce(view);

            assertThat(metrics.calls).containsExactly("started:orders", "completed:orders");
            assertThat(metrics.lastPositionAdvance).isEqualTo(0);
        }
    }

    /**
     * 覆盖 <code>runOnce 失败时 metrics 回调</code> 场景及其预期行为。
     */
    @Nested
    @DisplayName("runOnce 失败时 metrics 回调")
    class RunOnceFailureMetrics {

        /**
         * 验证<code>metrics.calls</code> 按顺序仅包含 <code>"started:orders"</code>、<code>"failed:orders"</code>，<code>metrics.lastError</code> 指向同一实例 <code>expectedError</code>。
         */
        @Test
        void runOnce_chunkReader抛异常_应触发started和failed回调() {
            RecordingMetrics metrics = new RecordingMetrics();
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, metrics);
            TestView view = new TestView("orders", "orders", 100, Arrays.asList("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            RuntimeException expectedError = new RuntimeException("chunk reader failed");
            when(chunkReader.read(any(), anyLong(), anyInt(), any()))
                    .thenThrow(expectedError);

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isSameAs(expectedError);

            assertThat(metrics.calls).containsExactly("started:orders", "failed:orders");
            assertThat(metrics.lastError).isSameAs(expectedError);
        }

        /**
         * 验证<code>runner.runOnce(null)</code> 抛出 <code>NullPointerException.class</code>，<code>metrics.calls</code>为空。
         */
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

    /**
     * 覆盖 <code>NoopProjectionMetrics</code> 场景及其预期行为。
     */
    @Nested
    @DisplayName("NoopProjectionMetrics")
    class NoopMetricsTests {

        /**
         * 验证<code>NoopProjectionMetrics.INSTANCE</code>非空。
         */
        @Test
        void noopMetrics_单例不为null() {
            assertThat(NoopProjectionMetrics.INSTANCE).isNotNull();
        }

        /**
         * 验证<code>result</code> 指向同一实例 <code>chunk</code>。
         */
        @Test
        void 使用noopMetrics_不影响正常流程() {
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader,
                    NoopProjectionMetrics.INSTANCE);
            TestView view = new TestView("orders", "orders", 100, Arrays.asList("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList("e1"), 1);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(chunk);

            EventChunk<String> result = runner.runOnce(view);

            assertThat(result).isSameAs(chunk);
        }

        /**
         * 在测试框架中执行 <code>Arrays.asList()</code>、<code>projectionService.readProjectionPosition()</code>、<code>chunkReader.read()</code>，验证该场景。
         */
        @Test
        void metrics参数为null_应默认使用noop() {
            ProjectionRunner<String> runner = new ProjectionRunner<>(projectionService, chunkReader, null);
            TestView view = new TestView("orders", "orders", 100, Arrays.asList("created"));

            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(0));

            // 不应抛异常
            runner.runOnce(view);
        }
    }
}
