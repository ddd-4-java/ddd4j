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

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionService</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionService projectionService;

    /** \u7528\u4e8e\u9694\u79bb <code>EventChunkReader&lt;String&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private EventChunkReader<String> chunkReader;

    /**
     * 可记录的 metrics 实现：记录所有回调调用，便于断言。
     */
    static class RecordingMetrics implements ProjectionMetrics {

        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        final List<String> calls = new ArrayList<>();
        /** \u6700\u8fd1\u4e00\u6b21\u56de\u8c03\u6536\u5230\u7684\u6295\u5f71\u6d41\u6807\u8bc6\u3002 */
        String lastStreamId;
        /** \u6700\u8fd1\u4e00\u6b21\u8bb0\u5f55\u7684\u5904\u7406\u4e8b\u4ef6\u6570\u3002 */
        int lastEventCount;
        /** \u6700\u8fd1\u4e00\u6b21\u6536\u5230\u7684\u6295\u5f71\u5904\u7406\u8017\u65f6\uff0c\u5355\u4f4d\u4e3a\u7eb3\u79d2\u3002 */
        long lastDurationNanos;
        /** \u6700\u8fd1\u4e00\u6b21\u6295\u5f71\u901a\u77e5\u4e2d\u89c2\u5bdf\u5230\u7684\u4f4d\u70b9\u63a8\u8fdb\u91cf\u3002 */
        long lastPositionAdvance;
        /** \u6700\u8fd1\u4e00\u6b21\u89c2\u5bdf\u5230\u7684\u88ab\u6d4b\u64cd\u4f5c\u9519\u8bef\u4fe1\u606f\u3002 */
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
         * @param streamId 投影流 ID
         * @param eventCount 本次处理的事件数量
         * @param durationNanos 本次运行耗时（纳秒）
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
         * @param error 导致失败的异常
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

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        final String name;
        /** \u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6\u3002 */
        final String streamId;
        /** \u5206\u6279\u8bfb\u53d6\u4e8b\u4ef6\u65f6\u914d\u7f6e\u7684\u5355\u6279\u5927\u5c0f\u3002 */
        final int chunkSize;
        /** \u5939\u5177\u5411\u6295\u5f71\u8fd0\u884c\u5668\u58f0\u660e\u8ba2\u9605\u7684\u4e8b\u4ef6\u7c7b\u578b\u3002 */
        final List<String> eventTypes;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>TestView</code> \u5b9e\u4f8b\u3002
         * @param name <code>name</code> \u5b57\u6bb5
         * @param streamId \u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6
         * @param chunkSize <code>chunkSize</code> \u5b57\u6bb5
         * @param eventTypes <code>eventTypes</code> \u5b57\u6bb5
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

    /** \u8986\u76d6 <code>runOnce \u6210\u529f\u65f6 metrics \u56de\u8c03</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("runOnce 成功时 metrics 回调")
    class RunOnceSuccessMetrics {

        /** \u9a8c\u8bc1<code>metrics.calls</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"started:orders"</code>\u3001<code>"completed:orders"</code>\uff0c<code>metrics.lastStreamId</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>metrics.lastDurationNanos</code> \u5927\u4e8e\u6216\u7b49\u4e8e <code>0</code>\u3002 */
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

        /** \u9a8c\u8bc1<code>metrics.calls</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"started:orders"</code>\u3001<code>"completed:orders"</code>\uff0c<code>metrics.lastEventCount</code> \u7b49\u4e8e <code>0</code>\uff0c<code>metrics.lastPositionAdvance</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

        /** \u9a8c\u8bc1<code>metrics.calls</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"started:orders"</code>\u3001<code>"completed:orders"</code>\uff0c<code>metrics.lastPositionAdvance</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u8986\u76d6 <code>runOnce \u5931\u8d25\u65f6 metrics \u56de\u8c03</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("runOnce 失败时 metrics 回调")
    class RunOnceFailureMetrics {

        /** \u9a8c\u8bc1<code>metrics.calls</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"started:orders"</code>\u3001<code>"failed:orders"</code>\uff0c<code>metrics.lastError</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>expectedError</code>\u3002 */
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

        /** \u9a8c\u8bc1<code>runner.runOnce(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>metrics.calls</code>\u4e3a\u7a7a\u3002 */
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

    /** \u8986\u76d6 <code>NoopProjectionMetrics</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("NoopProjectionMetrics")
    class NoopMetricsTests {

        /** \u9a8c\u8bc1<code>NoopProjectionMetrics.INSTANCE</code>\u975e\u7a7a\u3002 */
        @Test
        void noopMetrics_单例不为null() {
            assertThat(NoopProjectionMetrics.INSTANCE).isNotNull();
        }

        /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>chunk</code>\u3002 */
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

        /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>projectionService.readProjectionPosition()</code>\u3001<code>chunkReader.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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
