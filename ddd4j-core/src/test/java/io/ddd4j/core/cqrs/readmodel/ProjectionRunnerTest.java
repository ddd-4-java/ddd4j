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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ProjectionRunner} 单元测试。
 *
 * <p>覆盖 {@code runOnce} 的全部分支：正常流程、空 chunk、位置未前进、各种非法入参，
 * 以及 {@code runAll} 的多视图/空集合/null 场景。使用 Mockito 模拟
 * {@link ProjectionService} 与 {@link EventChunkReader}。
 *
 * @author PartMe.AI
 */
@DisplayName("ProjectionRunner")
@ExtendWith(MockitoExtension.class)
class ProjectionRunnerTest {

    /** \u7528\u4e8e\u9694\u79bb <code>ProjectionService</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProjectionService projectionService;

    /** \u7528\u4e8e\u9694\u79bb <code>EventChunkReader&lt;String&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private EventChunkReader<String> chunkReader;

    /** \u7528\u4e8e\u6267\u884c\u6295\u5f71\u6216\u6d4b\u8bd5\u63a2\u9488\u8def\u5f84\u7684\u8fd0\u884c\u5668\u3002 */
    private ProjectionRunner<String> runner;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>runner = new ProjectionRunner&lt;&gt;(projectionService, chunkReader)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        runner = new ProjectionRunner<>(projectionService, chunkReader);
    }

    /** \u9a8c\u8bc1<code>service.readProjectionPosition("gap")</code> \u7b49\u4e8e <code>20</code>\uff0c<code>starts</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>10L</code>\u3001<code>20L</code>\uff0c<code>view.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"event-11"</code>\u3001<code>"event-19"</code>\u3002 */
    @Test
    void filteredGapUsesSourcePositionAndResumesFromSavedCheckpoint() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        service.updateProjectionPosition("gap", 10);
        List<Long> starts = new java.util.ArrayList<>();
        ProjectionRunner<String> shared = new ProjectionRunner<>(service, (id, from, size, types) -> {
            starts.add(from);
            return from == 10 ? new EventChunk<>(Arrays.asList("event-11", "event-19"), 20)
                    : EventChunk.empty(from);
        });
        TestView view = new TestView("gap", null, 100, Collections.singletonList("created"));
        shared.runOnce(view);
        shared.runOnce(view);
        assertThat(service.readProjectionPosition("gap")).isEqualTo(20);
        assertThat(starts).containsExactly(10L, 20L);
        assertThat(view.handled).containsExactly("event-11", "event-19");
    }

    /** \u9a8c\u8bc1<code>shared.runOnce(this)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>service.readProjectionPosition("guard")</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void sameStreamRecursiveExecutionIsRejectedAndGuardIsReleased() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        ProjectionRunner<String> shared = new ProjectionRunner<>(service,
                (id, from, size, types) -> new EventChunk<>(Collections.singletonList("event"), from + 1));
        AtomicInteger calls = new AtomicInteger();
        TestView view = new TestView("guard", null, 100, Collections.singletonList("created")) {
            @Override
            public void handleEvents(Collection<String> events) {
                if (calls.incrementAndGet() == 1) {
                    assertThatThrownBy(() -> shared.runOnce(this))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("guard");
                }
            }
        };
        shared.runOnce(view);
        shared.runOnce(view);
        assertThat(calls).hasValue(2);
        assertThat(service.readProjectionPosition("guard")).isEqualTo(2);
    }

    /**
     * \u9a8c\u8bc1<code>entered.await(5, java.util.concurrent.TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>shared.getProjectionStatus("same-stream").running()</code>\u4e3a\u771f\uff0c<code>service.readProjectionPosition("same-stream")</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void concurrentCallsShareStreamGuardButDifferentStreamsCanRun() throws Exception {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        ProjectionRunner<String> shared = new ProjectionRunner<>(service,
                (id, from, size, types) -> new EventChunk<>(Collections.singletonList("event"), from + 1));
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        TestView blocked = new TestView("blocked", "same-stream", 100, Collections.emptyList()) {
            @Override
            public void handleEvents(Collection<String> events) {
                entered.countDown();
                try {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test timed out");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
        };
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<?> active = executor.submit(() -> shared.runOnce(blocked));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(shared.getProjectionStatus("same-stream").running()).isTrue();
            assertThatThrownBy(() -> shared.runOnce(new TestView("alias", "same-stream", 100, Collections.emptyList())))
                    .isInstanceOf(IllegalStateException.class);
            shared.runOnce(new TestView("independent", null, 100, Collections.emptyList()));
            assertThat(service.readProjectionPosition("independent")).isEqualTo(1);
            release.countDown();
            active.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(shared.getProjectionStatus("same-stream").running()).isFalse();
            assertThat(service.readProjectionPosition("same-stream")).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** \u9a8c\u8bc1<code>view.handled.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>service.readProjectionPosition("orders")</code> \u7b49\u4e8e <code>1L</code>\u3002 */
    @Test
    void shouldRunChunkAndAdvancePosition() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        ProjectionRunner<String> runner = new ProjectionRunner<String>(service, new FixedReader());
        RecordingView view = new RecordingView("orders", false);
        runner.runOnce(view);
        assertEquals(1, view.handled.get());
        assertEquals(1L, service.readProjectionPosition("orders"));
    }

    /** \u9a8c\u8bc1<code>runner.runAll(Arrays.asList(failing, succeeding))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>metrics.circuitOpened.get()</code> \u7b49\u4e8e <code>ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD</code>\uff0c<code>succeeding.handled.get()</code> \u7b49\u4e8e <code>ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD</code>\u3002 */
    @Test
    void shouldPropagateRunAllFailureButIsolateRunAllIsolatedFailures() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        RecordingMetrics metrics = new RecordingMetrics();
        ProjectionRunner<String> runner = new ProjectionRunner<String>(service, new FixedReader(), metrics);
        RecordingView failing = new RecordingView("failing", true);
        RecordingView succeeding = new RecordingView("succeeding", false);
        assertThrows(IllegalStateException.class, () -> runner.runAll(Arrays.asList(failing, succeeding)));
        for (int i = 0; i < ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD; i++) {
            runner.runAllIsolated(Arrays.asList(failing, succeeding));
        }
        assertEquals(ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD, metrics.circuitOpened.get());
        assertEquals(ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD, succeeding.handled.get());
    }

    /**
     * 真实可记录的 view 实现：记录被 handle 的事件，便于断言交互契约。
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
        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        final List<String> handled = new java.util.ArrayList<>();

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
         * 处理本批投影事件。
         *
         * <p>测试夹具会调用 <code>handled.addAll(events)</code>。</p>
         *
         * @param events 事件列表
         */
        @Override
        public void handleEvents(Collection<String> events) {
            handled.addAll(events);
        }
    }

    /** \u5b9e\u73b0 <code>EventChunkReader</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>read()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class FixedReader implements EventChunkReader<String> {
        /**
         * 读取调用指定的数据或事件流。
         *
         * <p>测试夹具会调用 <code>Collections.singletonList(streamId)</code>。</p>
         *
         * @param streamId 投影流 ID
         * @param from 用于此操作的 <code>from</code>（<code>long</code>）
         * @param size 用于此操作的 <code>size</code>（<code>int</code>）
         * @param types 用于此操作的 <code>types</code>（<code>Collection&lt;String&gt;</code>）
         * @return 事件块
         */
        @Override
        public EventChunk<String> read(String streamId, long from, int size, Collection<String> types) {
            return new EventChunk<String>(Collections.singletonList(streamId), from + 1L);
        }
    }

    /** \u4f9b\u8fd0\u884c\u65f6\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u4f4d\u70b9\u63a8\u8fdb\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    private static final class RecordingView implements ProjectionView<String> {
        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;
        /** \u4ee4\u5f53\u524d\u66ff\u8eab\u5728\u6307\u5b9a\u6d4b\u8bd5\u8def\u5f84\u4e2d\u8fd4\u56de\u5931\u8d25\u3002 */
        private final boolean fail;
        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        private final AtomicInteger handled = new AtomicInteger();

        /**
         * \u8bbe\u7f6e <code>name</code>\u3001<code>fail</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RecordingView</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @param fail \u7528\u4e8e\u521d\u59cb\u5316 <code>RecordingView</code> \u7684 <code>boolean</code> \u503c
         */
        private RecordingView(String name, boolean fail) {
            this.name = name;
            this.fail = fail;
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
         * 返回投影视图配置的调度表达式。
         *
         * @return 实际返回<code>"* * * * *"</code>，供调用方取得当前值
         */
        @Override
        public String getCron() {
            return "* * * * *";
        }

        /**
         * 返回投影或处理器关心的事件类型。
         *
         * <p>测试夹具会调用 <code>Collections.emptyList()</code>。</p>
         *
         * @return 实际返回<code>Collections.emptyList()</code>
         */
        @Override
        public Collection<String> getEventTypes() {
            return Collections.emptyList();
        }

        /**
         * 处理本批投影事件。
         *
         * <p>满足 <code>fail</code> 时抛出 <code>IllegalStateException</code>。 测试夹具会调用 <code>handled.incrementAndGet()</code>。</p>
         *
         * @param events 事件列表
         */
        @Override
        public void handleEvents(Collection<String> events) {
            if (fail) {
                throw new IllegalStateException("expected");
            }
            handled.incrementAndGet();
        }
    }

    /** \u5b9e\u73b0 <code>ProjectionMetrics</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>onCircuitOpened()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class RecordingMetrics implements ProjectionMetrics {
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        private final AtomicInteger circuitOpened = new AtomicInteger();

        /**
         * 同一视图连续失败达到 {@link ProjectionRunner#CONSECUTIVE_FAILURE_THRESHOLD} 次时。
         *
         * <p>测试夹具会调用 <code>circuitOpened.set(consecutiveFailures)</code>。</p>
         *
         * @param viewName 投影视图名
         * @param consecutiveFailures 传给 <code>circuitOpened.set(consecutiveFailures)</code>
         */
        @Override
        public void onCircuitOpened(String viewName, int consecutiveFailures) {
            circuitOpened.set(consecutiveFailures);
        }
    }

    /** \u8986\u76d6 <code>\u6784\u9020\u5668</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("构造器")
    class Constructor {

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void 构造器_projectionService为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionRunner<String>(null, chunkReader))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("projectionService must not be null");
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void 构造器_chunkReader为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionRunner<String>(projectionService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chunkReader must not be null");
        }
    }

    /** \u9a8c\u8bc1\u6295\u5f71\u8fd0\u884c\u4e00\u8f6e\u65f6\u5904\u7406\u4e8b\u4ef6\u5e76\u63d0\u4ea4\u4f4d\u7f6e\u7684\u6b63\u5e38\u8def\u5f84\u3002 */
    @Nested
    @DisplayName("runOnce 正常流程")
    class RunOnceHappyPath {

        /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>chunk</code>\uff0c<code>view.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\u3001<code>"e2"</code>\u3002 */
        @Test
        void runOnce_chunk有事件_应处理事件并推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created", "deleted"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList("e1", "e2"), 2);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(Arrays.asList("created", "deleted"))))
                    .thenReturn(chunk);

            EventChunk<String> result = runner.runOnce(view);

            assertThat(result).isSameAs(chunk);
            assertThat(view.handled).containsExactly("e1", "e2");
            verify(projectionService).updateProjectionPosition("person-list", 2L);
        }

        /** \u9a8c\u8bc1<code>view.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\u3001<code>"e2"</code>\u3002 */
        @Test
        void runOnce_chunk有事件_应调用handleEvents传入事件列表() {
            // 使用真实 view 而非 mock，以便校验 handleEvents 被调用
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList("e1", "e2"), 2);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(chunk);

            runner.runOnce(view);

            assertThat(view.handled).containsExactly("e1", "e2");
        }

        /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>projectionService.readProjectionPosition()</code>\u3001<code>chunkReader.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
        @Test
        void runOnce_chunk有事件_应调用updateProjectionPosition推进到nextEventNumber() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(5L);
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList("e1"), 9);
            when(chunkReader.read(eq("person-list"), eq(5L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(chunk);

            runner.runOnce(view);

            verify(projectionService, times(1)).updateProjectionPosition("person-list", 9L);
        }

        /** \u9a8c\u8bc1<code>fromCaptor.getValue()</code> \u7b49\u4e8e <code>7L</code>\u3002 */
        @Test
        void runOnce_应按读取到的位置作为fromEventNumber调用chunkReader() {
            TestView view = new TestView("person-list", "person-list", 50, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(7L);
            when(chunkReader.read(eq("person-list"), eq(7L), eq(50), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Collections.emptyList(), 7L));

            runner.runOnce(view);

            ArgumentCaptor<Long> fromCaptor = ArgumentCaptor.forClass(Long.class);
            verify(chunkReader).read(eq("person-list"), fromCaptor.capture(), eq(50), eq(Arrays.asList("created")));
            assertThat(fromCaptor.getValue()).isEqualTo(7L);
        }
    }

    /** \u9a8c\u8bc1\u6295\u5f71\u8bfb\u53d6\u7a7a\u6279\u6b21\u65f6\u7684\u5904\u7406\u7ed3\u679c\u3002 */
    @Nested
    @DisplayName("runOnce 空 chunk")
    class RunOnceEmptyChunk {

        /** \u9a8c\u8bc1<code>view.handled</code>\u4e3a\u7a7a\u3002 */
        @Test
        void runOnce_chunk为空_不应调用handleEvents() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(0));

            runner.runOnce(view);

            assertThat(view.handled).isEmpty();
        }

        /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>projectionService.readProjectionPosition()</code>\u3001<code>chunkReader.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
        @Test
        void runOnce_chunk为空且nextEventNumber未前进_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(3L);
            when(chunkReader.read(eq("person-list"), eq(3L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(3));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }

        /** \u9a8c\u8bc1<code>view.handled</code>\u4e3a\u7a7a\u3002 */
        @Test
        void runOnce_chunk为空但nextEventNumber前进_仍应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(3L);
            // chunk 无事件但 nextEventNumber 前进了（例如已被其他消费者处理）
            when(chunkReader.read(eq("person-list"), eq(3L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(8));

            runner.runOnce(view);

            verify(projectionService, times(1)).updateProjectionPosition("person-list", 8L);
            assertThat(view.handled).isEmpty();
        }
    }

    /** \u9a8c\u8bc1\u4e8b\u4ef6\u6279\u6b21\u4f4d\u70b9\u672a\u63a8\u8fdb\u65f6\u7684\u6295\u5f71\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("runOnce nextEventNumber 未前进")
    class RunOnceNoAdvance {

        /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>projectionService.readProjectionPosition()</code>\u3001<code>chunkReader.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
        @Test
        void runOnce_nextEventNumber等于当前位置_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(5L);
            // 即使有事件，但 nextEventNumber 未前进（理论上不应发生，但需保护）
            when(chunkReader.read(eq("person-list"), eq(5L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e1"), 5));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }

        /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>projectionService.readProjectionPosition()</code>\u3001<code>chunkReader.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
        @Test
        void runOnce_nextEventNumber小于当前位置_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(5L);
            when(chunkReader.read(eq("person-list"), eq(5L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e1"), 3));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }
    }

    /** \u9a8c\u8bc1\u5355\u8f6e\u6295\u5f71\u5904\u7406\u5bf9\u8f93\u5165\u503c\u548c\u8bfb\u53d6\u7ed3\u679c\u7684\u7ea6\u675f\u3002 */
    @Nested
    @DisplayName("runOnce 参数校验")
    class RunOnceValidation {

        /** \u9a8c\u8bc1<code>runner.runOnce(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void runOnce_view为null_应抛NullPointerException() {
            assertThatThrownBy(() -> runner.runOnce(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("view must not be null");

            verifyNoInteractions(projectionService, chunkReader);
        }

        /** \u9a8c\u8bc1<code>runner.runOnce(view)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void runOnce_viewName为null_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView(null, "person-list", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view name must not be blank");
        }

        /** \u9a8c\u8bc1<code>runner.runOnce(view)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void runOnce_viewName为空白_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("   ", "person-list", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view name must not be blank");
        }

        /** \u9a8c\u8bc1<code>runner.runOnce(view)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void runOnce_streamId为空_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view streamId must not be blank");
        }

        /** \u9a8c\u8bc1<code>runner.runOnce(view)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void runOnce_chunkSize为零_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "person-list", 0, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view chunkSize must be positive");
        }

        /** \u9a8c\u8bc1<code>runner.runOnce(view)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void runOnce_chunkSize为负数_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "person-list", -5, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view chunkSize must be positive");
        }

        /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>projectionService.readProjectionPosition()</code>\u3001<code>chunkReader.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
        @Test
        void runOnce_streamId默认回退到name_应合法执行() {
            // streamId 为 null 时 getStreamId 默认返回 name，应当合法
            TestView view = new TestView("orders", null, 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(0));

            runner.runOnce(view);

            verify(projectionService).readProjectionPosition("orders");
        }
    }

    /** \u9a8c\u8bc1\u4e8b\u4ef6\u8bfb\u53d6\u5668\u8fd4\u56de\u7a7a\u5f15\u7528\u65f6\u7684\u517c\u5bb9\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("runOnce chunkReader 返回 null")
    class RunOnceNullChunk {

        /** \u9a8c\u8bc1<code>runner.runOnce(view)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void runOnce_chunkReader返回null_应抛NullPointerException() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(any(), anyLong(), anyInt(), any()))
                    .thenReturn(null);

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chunkReader must not return null");

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }
    }

    /** \u9a8c\u8bc1\u6279\u91cf\u8fd0\u884c\u591a\u6761\u6295\u5f71\u6d41\u65f6\u7684\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("runAll 多视图")
    class RunAll {

        /** \u9a8c\u8bc1<code>first.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\uff0c<code>second.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e2"</code>\u3002 */
        @Test
        void runAll_多个视图_应依次执行每个视图() {
            TestView first = new TestView("first", "first", 100, Arrays.asList("created"));
            TestView second = new TestView("second", "second", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("first")).thenReturn(0L);
            when(projectionService.readProjectionPosition("second")).thenReturn(0L);
            when(chunkReader.read(eq("first"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e1"), 1));
            when(chunkReader.read(eq("second"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e2"), 1));

            runner.runAll(Arrays.asList(first, second));

            assertThat(first.handled).containsExactly("e1");
            assertThat(second.handled).containsExactly("e2");
            verify(projectionService).updateProjectionPosition("first", 1L);
            verify(projectionService).updateProjectionPosition("second", 1L);
        }

        /** \u9a8c\u8bc1\u672a\u8c03\u7528 <code>projectionService, chunkReader</code>\u3002 */
        @Test
        void runAll_null集合_不应抛异常() {
            // runAll 内部使用 CollKit.isEmpty，null 视为空直接返回
            runner.runAll(null);

            verifyNoInteractions(projectionService, chunkReader);
        }

        /** \u9a8c\u8bc1\u672a\u8c03\u7528 <code>projectionService, chunkReader</code>\u3002 */
        @Test
        void runAll_空集合_不应抛异常() {
            runner.runAll(Collections.emptyList());

            verifyNoInteractions(projectionService, chunkReader);
        }

        /** \u9a8c\u8bc1<code>runner.runAll(Arrays.asList(first, second))</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void runAll_其中一个视图抛异常_应向上传播中断后续视图() {
            TestView first = new TestView("first", "first", 0, Arrays.asList("created")); // chunkSize 非法
            TestView second = new TestView("second", "second", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runAll(Arrays.asList(first, second)))
                    .isInstanceOf(IllegalArgumentException.class);

            // second 不应被执行（无任何 read 调用）
            verify(projectionService, never()).readProjectionPosition(any());
        }

        /** \u9a8c\u8bc1<code>second.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e2"</code>\u3002 */
        @Test
        void runAllIsolated_其中一个视图抛异常_应继续执行后续视图() {
            TestView first = new TestView("first", "first", 0, Arrays.asList("created"));
            TestView second = new TestView("second", "second", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("second")).thenReturn(0L);
            when(chunkReader.read(eq("second"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e2"), 1));

            runner.runAllIsolated(Arrays.asList(first, second));

            assertThat(second.handled).containsExactly("e2");
            verify(projectionService).readProjectionPosition("second");
            verify(projectionService).updateProjectionPosition("second", 1L);
        }

        /** \u9a8c\u8bc1<code>view.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\u3002 */
        @Test
        void runAll_单视图集合_应等价于runOnce() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(new EventChunk<>(Arrays.asList("e1"), 1));

            runner.runAll(Arrays.asList(view));

            assertThat(view.handled).containsExactly("e1");
            verify(projectionService).updateProjectionPosition("person-list", 1L);
        }
    }
}
