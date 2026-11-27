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
     * 用于执行投影或测试探针路径的运行器。
     */
    private ProjectionRunner<String> runner;

    /**
     * 根据本次调用更新 <code>runner = new ProjectionRunner&lt;&gt;(projectionService, chunkReader)</code>，用于后续测试步骤。
     */
    @BeforeEach
    void setUp() {
        runner = new ProjectionRunner<>(projectionService, chunkReader);
    }

    /**
     * 验证<code>service.readProjectionPosition("gap")</code> 等于 <code>20</code>，<code>starts</code> 按顺序仅包含 <code>10L</code>、<code>20L</code>，<code>view.handled</code> 按顺序仅包含 <code>"event-11"</code>、<code>"event-19"</code>。
     */
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

    /**
     * 验证<code>shared.runOnce(this)</code> 抛出 <code>IllegalStateException.class</code>，<code>service.readProjectionPosition("guard")</code> 等于 <code>2</code>。
     */
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
     * 验证<code>entered.await(5, java.util.concurrent.TimeUnit.SECONDS)</code>为真，<code>shared.getProjectionStatus("same-stream").running()</code>为真，<code>service.readProjectionPosition("same-stream")</code> 等于 <code>1</code>。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 验证<code>view.handled.get()</code> 等于 <code>1</code>，<code>service.readProjectionPosition("orders")</code> 等于 <code>1L</code>。
     */
    @Test
    void shouldRunChunkAndAdvancePosition() {
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        ProjectionRunner<String> runner = new ProjectionRunner<String>(service, new FixedReader());
        RecordingView view = new RecordingView("orders", false);
        runner.runOnce(view);
        assertEquals(1, view.handled.get());
        assertEquals(1L, service.readProjectionPosition("orders"));
    }

    /**
     * 验证<code>runner.runAll(Arrays.asList(failing, succeeding))</code> 抛出 <code>IllegalStateException.class</code>，<code>metrics.circuitOpened.get()</code> 等于 <code>ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD</code>，<code>succeeding.handled.get()</code> 等于 <code>ProjectionRunner.CONSECUTIVE_FAILURE_THRESHOLD</code>。
     */
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
         * 当前测试中已经交给处理器的记录。
         */
        final List<String> handled = new java.util.ArrayList<>();

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

    /**
     * 实现 <code>EventChunkReader</code>，供测试调用 <code>read()</code> 并观察结果。
     */
    private static final class FixedReader implements EventChunkReader<String> {
        /**
         * 读取调用指定的数据或事件流。
         *
         * <p>测试夹具会调用 <code>Collections.singletonList(streamId)</code>。</p>
         *
         * @param streamId 投影流 ID
         * @param from     用于此操作的 <code>from</code>（<code>long</code>）
         * @param size     用于此操作的 <code>size</code>（<code>int</code>）
         * @param types    用于此操作的 <code>types</code>（<code>Collection&lt;String&gt;</code>）
         * @return 事件块
         */
        @Override
        public EventChunk<String> read(String streamId, long from, int size, Collection<String> types) {
            return new EventChunk<String>(Collections.singletonList(streamId), from + 1L);
        }
    }

    /**
     * 供运行时测试验证事件应用和位点推进的投影视图。
     */
    private static final class RecordingView implements ProjectionView<String> {
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;
        /**
         * 令当前替身在指定测试路径中返回失败。
         */
        private final boolean fail;
        /**
         * 当前测试中已经交给处理器的记录。
         */
        private final AtomicInteger handled = new AtomicInteger();

        /**
         * 设置 <code>name</code>、<code>fail</code>，用于构造 <code>RecordingView</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         * @param fail 用于初始化 <code>RecordingView</code> 的 <code>boolean</code> 值
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

    /**
     * 实现 <code>ProjectionMetrics</code>，供测试调用 <code>onCircuitOpened()</code> 并观察结果。
     */
    private static final class RecordingMetrics implements ProjectionMetrics {
        /**
         * 在测试线程之间共享当前操作次数或更新数量的计数器。
         */
        private final AtomicInteger circuitOpened = new AtomicInteger();

        /**
         * 同一视图连续失败达到 {@link ProjectionRunner#CONSECUTIVE_FAILURE_THRESHOLD} 次时。
         *
         * <p>测试夹具会调用 <code>circuitOpened.set(consecutiveFailures)</code>。</p>
         *
         * @param viewName            投影视图名
         * @param consecutiveFailures 传给 <code>circuitOpened.set(consecutiveFailures)</code>
         */
        @Override
        public void onCircuitOpened(String viewName, int consecutiveFailures) {
            circuitOpened.set(consecutiveFailures);
        }
    }

    /**
     * 覆盖 <code>构造器</code> 场景及其预期行为。
     */
    @Nested
    @DisplayName("构造器")
    class Constructor {

        /**
         * 验证被测调用 抛出 <code>NullPointerException.class</code>。
         */
        @Test
        void 构造器_projectionService为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionRunner<String>(null, chunkReader))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("projectionService must not be null");
        }

        /**
         * 验证被测调用 抛出 <code>NullPointerException.class</code>。
         */
        @Test
        void 构造器_chunkReader为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionRunner<String>(projectionService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chunkReader must not be null");
        }
    }

    /**
     * 验证投影运行一轮时处理事件并提交位置的正常路径。
     */
    @Nested
    @DisplayName("runOnce 正常流程")
    class RunOnceHappyPath {

        /**
         * 验证<code>result</code> 指向同一实例 <code>chunk</code>，<code>view.handled</code> 按顺序仅包含 <code>"e1"</code>、<code>"e2"</code>。
         */
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

        /**
         * 验证<code>view.handled</code> 按顺序仅包含 <code>"e1"</code>、<code>"e2"</code>。
         */
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

        /**
         * 在测试框架中执行 <code>Arrays.asList()</code>、<code>projectionService.readProjectionPosition()</code>、<code>chunkReader.read()</code>，验证该场景。
         */
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

        /**
         * 验证<code>fromCaptor.getValue()</code> 等于 <code>7L</code>。
         */
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

    /**
     * 验证投影读取空批次时的处理结果。
     */
    @Nested
    @DisplayName("runOnce 空 chunk")
    class RunOnceEmptyChunk {

        /**
         * 验证<code>view.handled</code>为空。
         */
        @Test
        void runOnce_chunk为空_不应调用handleEvents() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(0));

            runner.runOnce(view);

            assertThat(view.handled).isEmpty();
        }

        /**
         * 在测试框架中执行 <code>Arrays.asList()</code>、<code>projectionService.readProjectionPosition()</code>、<code>chunkReader.read()</code>，验证该场景。
         */
        @Test
        void runOnce_chunk为空且nextEventNumber未前进_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, Arrays.asList("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(3L);
            when(chunkReader.read(eq("person-list"), eq(3L), eq(100), eq(Arrays.asList("created"))))
                    .thenReturn(EventChunk.empty(3));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }

        /**
         * 验证<code>view.handled</code>为空。
         */
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

    /**
     * 验证事件批次位点未推进时的投影行为。
     */
    @Nested
    @DisplayName("runOnce nextEventNumber 未前进")
    class RunOnceNoAdvance {

        /**
         * 在测试框架中执行 <code>Arrays.asList()</code>、<code>projectionService.readProjectionPosition()</code>、<code>chunkReader.read()</code>，验证该场景。
         */
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

        /**
         * 在测试框架中执行 <code>Arrays.asList()</code>、<code>projectionService.readProjectionPosition()</code>、<code>chunkReader.read()</code>，验证该场景。
         */
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

    /**
     * 验证单轮投影处理对输入值和读取结果的约束。
     */
    @Nested
    @DisplayName("runOnce 参数校验")
    class RunOnceValidation {

        /**
         * 验证<code>runner.runOnce(null)</code> 抛出 <code>NullPointerException.class</code>。
         */
        @Test
        void runOnce_view为null_应抛NullPointerException() {
            assertThatThrownBy(() -> runner.runOnce(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("view must not be null");

            verifyNoInteractions(projectionService, chunkReader);
        }

        /**
         * 验证<code>runner.runOnce(view)</code> 抛出 <code>IllegalArgumentException.class</code>。
         */
        @Test
        void runOnce_viewName为null_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView(null, "person-list", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view name must not be blank");
        }

        /**
         * 验证<code>runner.runOnce(view)</code> 抛出 <code>IllegalArgumentException.class</code>。
         */
        @Test
        void runOnce_viewName为空白_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("   ", "person-list", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view name must not be blank");
        }

        /**
         * 验证<code>runner.runOnce(view)</code> 抛出 <code>IllegalArgumentException.class</code>。
         */
        @Test
        void runOnce_streamId为空_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view streamId must not be blank");
        }

        /**
         * 验证<code>runner.runOnce(view)</code> 抛出 <code>IllegalArgumentException.class</code>。
         */
        @Test
        void runOnce_chunkSize为零_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "person-list", 0, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view chunkSize must be positive");
        }

        /**
         * 验证<code>runner.runOnce(view)</code> 抛出 <code>IllegalArgumentException.class</code>。
         */
        @Test
        void runOnce_chunkSize为负数_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "person-list", -5, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view chunkSize must be positive");
        }

        /**
         * 在测试框架中执行 <code>Arrays.asList()</code>、<code>projectionService.readProjectionPosition()</code>、<code>chunkReader.read()</code>，验证该场景。
         */
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

    /**
     * 验证事件读取器返回空引用时的兼容行为。
     */
    @Nested
    @DisplayName("runOnce chunkReader 返回 null")
    class RunOnceNullChunk {

        /**
         * 验证<code>runner.runOnce(view)</code> 抛出 <code>NullPointerException.class</code>。
         */
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

    /**
     * 验证批量运行多条投影流时的行为。
     */
    @Nested
    @DisplayName("runAll 多视图")
    class RunAll {

        /**
         * 验证<code>first.handled</code> 按顺序仅包含 <code>"e1"</code>，<code>second.handled</code> 按顺序仅包含 <code>"e2"</code>。
         */
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

        /**
         * 验证未调用 <code>projectionService, chunkReader</code>。
         */
        @Test
        void runAll_null集合_不应抛异常() {
            // runAll 内部使用 CollKit.isEmpty，null 视为空直接返回
            runner.runAll(null);

            verifyNoInteractions(projectionService, chunkReader);
        }

        /**
         * 验证未调用 <code>projectionService, chunkReader</code>。
         */
        @Test
        void runAll_空集合_不应抛异常() {
            runner.runAll(Collections.emptyList());

            verifyNoInteractions(projectionService, chunkReader);
        }

        /**
         * 验证<code>runner.runAll(Arrays.asList(first, second))</code> 抛出 <code>IllegalArgumentException.class</code>。
         */
        @Test
        void runAll_其中一个视图抛异常_应向上传播中断后续视图() {
            TestView first = new TestView("first", "first", 0, Arrays.asList("created")); // chunkSize 非法
            TestView second = new TestView("second", "second", 100, Arrays.asList("created"));

            assertThatThrownBy(() -> runner.runAll(Arrays.asList(first, second)))
                    .isInstanceOf(IllegalArgumentException.class);

            // second 不应被执行（无任何 read 调用）
            verify(projectionService, never()).readProjectionPosition(any());
        }

        /**
         * 验证<code>second.handled</code> 按顺序仅包含 <code>"e2"</code>。
         */
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

        /**
         * 验证<code>view.handled</code> 按顺序仅包含 <code>"e1"</code>。
         */
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
