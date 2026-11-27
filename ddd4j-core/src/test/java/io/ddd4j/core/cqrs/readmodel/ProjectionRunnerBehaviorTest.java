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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class ProjectionRunnerBehaviorTest {

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
