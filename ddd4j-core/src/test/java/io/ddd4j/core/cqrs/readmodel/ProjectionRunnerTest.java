package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
class ProjectionRunnerTest {

    @Mock
    private ProjectionService projectionService;

    @Mock
    private EventChunkReader<String> chunkReader;

    private ProjectionRunner<String> runner;

    @BeforeEach
    void setUp() {
        runner = new ProjectionRunner<>(projectionService, chunkReader);
    }

    /**
     * 真实可记录的 view 实现：记录被 handle 的事件，便于断言交互契约。
     */
    static class TestView implements ProjectionView<String> {

        final String name;
        final String streamId;
        final int chunkSize;
        final List<String> eventTypes;
        final List<String> handled = new java.util.ArrayList<>();

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
            handled.addAll(events);
        }
    }

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        void 构造器_projectionService为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionRunner<String>(null, chunkReader))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("projectionService must not be null");
        }

        @Test
        void 构造器_chunkReader为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionRunner<String>(projectionService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chunkReader must not be null");
        }
    }

    @Nested
    @DisplayName("runOnce 正常流程")
    class RunOnceHappyPath {

        @Test
        void runOnce_chunk有事件_应处理事件并推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created", "deleted"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(List.of("e1", "e2"), 2);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(List.of("created", "deleted"))))
                    .thenReturn(chunk);

            EventChunk<String> result = runner.runOnce(view);

            assertThat(result).isSameAs(chunk);
            assertThat(view.handled).containsExactly("e1", "e2");
            verify(projectionService).updateProjectionPosition("person-list", 2L);
        }

        @Test
        void runOnce_chunk有事件_应调用handleEvents传入事件列表() {
            // 使用真实 view 而非 mock，以便校验 handleEvents 被调用
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            EventChunk<String> chunk = new EventChunk<>(List.of("e1", "e2"), 2);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(chunk);

            runner.runOnce(view);

            assertThat(view.handled).containsExactly("e1", "e2");
        }

        @Test
        void runOnce_chunk有事件_应调用updateProjectionPosition推进到nextEventNumber() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(5L);
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 9);
            when(chunkReader.read(eq("person-list"), eq(5L), eq(100), eq(List.of("created"))))
                    .thenReturn(chunk);

            runner.runOnce(view);

            verify(projectionService, times(1)).updateProjectionPosition("person-list", 9L);
        }

        @Test
        void runOnce_应按读取到的位置作为fromEventNumber调用chunkReader() {
            TestView view = new TestView("person-list", "person-list", 50, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(7L);
            when(chunkReader.read(eq("person-list"), eq(7L), eq(50), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of(), 7L));

            runner.runOnce(view);

            ArgumentCaptor<Long> fromCaptor = ArgumentCaptor.forClass(Long.class);
            verify(chunkReader).read(eq("person-list"), fromCaptor.capture(), eq(50), eq(List.of("created")));
            assertThat(fromCaptor.getValue()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("runOnce 空 chunk")
    class RunOnceEmptyChunk {

        @Test
        void runOnce_chunk为空_不应调用handleEvents() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(EventChunk.empty(0));

            runner.runOnce(view);

            assertThat(view.handled).isEmpty();
        }

        @Test
        void runOnce_chunk为空且nextEventNumber未前进_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(3L);
            when(chunkReader.read(eq("person-list"), eq(3L), eq(100), eq(List.of("created"))))
                    .thenReturn(EventChunk.empty(3));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }

        @Test
        void runOnce_chunk为空但nextEventNumber前进_仍应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(3L);
            // chunk 无事件但 nextEventNumber 前进了（例如已被其他消费者处理）
            when(chunkReader.read(eq("person-list"), eq(3L), eq(100), eq(List.of("created"))))
                    .thenReturn(EventChunk.empty(8));

            runner.runOnce(view);

            verify(projectionService, times(1)).updateProjectionPosition("person-list", 8L);
            assertThat(view.handled).isEmpty();
        }
    }

    @Nested
    @DisplayName("runOnce nextEventNumber 未前进")
    class RunOnceNoAdvance {

        @Test
        void runOnce_nextEventNumber等于当前位置_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(5L);
            // 即使有事件，但 nextEventNumber 未前进（理论上不应发生，但需保护）
            when(chunkReader.read(eq("person-list"), eq(5L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e1"), 5));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }

        @Test
        void runOnce_nextEventNumber小于当前位置_不应推进位置() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(5L);
            when(chunkReader.read(eq("person-list"), eq(5L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e1"), 3));

            runner.runOnce(view);

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("runOnce 参数校验")
    class RunOnceValidation {

        @Test
        void runOnce_view为null_应抛NullPointerException() {
            assertThatThrownBy(() -> runner.runOnce(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("view must not be null");

            verifyNoInteractions(projectionService, chunkReader);
        }

        @Test
        void runOnce_viewName为null_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView(null, "person-list", 100, List.of("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view name must not be blank");
        }

        @Test
        void runOnce_viewName为空白_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("   ", "person-list", 100, List.of("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view name must not be blank");
        }

        @Test
        void runOnce_streamId为空_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "", 100, List.of("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view streamId must not be blank");
        }

        @Test
        void runOnce_chunkSize为零_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "person-list", 0, List.of("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view chunkSize must be positive");
        }

        @Test
        void runOnce_chunkSize为负数_应抛IllegalArgumentException() {
            ProjectionView<String> view = new TestView("person-list", "person-list", -5, List.of("created"));

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("view chunkSize must be positive");
        }

        @Test
        void runOnce_streamId默认回退到name_应合法执行() {
            // streamId 为 null 时 getStreamId 默认返回 name，应当合法
            TestView view = new TestView("orders", null, 100, List.of("created"));
            when(projectionService.readProjectionPosition("orders")).thenReturn(0L);
            when(chunkReader.read(eq("orders"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(EventChunk.empty(0));

            runner.runOnce(view);

            verify(projectionService).readProjectionPosition("orders");
        }
    }

    @Nested
    @DisplayName("runOnce chunkReader 返回 null")
    class RunOnceNullChunk {

        @Test
        void runOnce_chunkReader返回null_应抛NullPointerException() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(any(), anyLong(), anyInt(), any()))
                    .thenReturn(null);

            assertThatThrownBy(() -> runner.runOnce(view))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chunkReader must not return null");

            verify(projectionService, never()).updateProjectionPosition(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("runAll 多视图")
    class RunAll {

        @Test
        void runAll_多个视图_应依次执行每个视图() {
            TestView first = new TestView("first", "first", 100, List.of("created"));
            TestView second = new TestView("second", "second", 100, List.of("created"));
            when(projectionService.readProjectionPosition("first")).thenReturn(0L);
            when(projectionService.readProjectionPosition("second")).thenReturn(0L);
            when(chunkReader.read(eq("first"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e1"), 1));
            when(chunkReader.read(eq("second"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e2"), 1));

            runner.runAll(List.of(first, second));

            assertThat(first.handled).containsExactly("e1");
            assertThat(second.handled).containsExactly("e2");
            verify(projectionService).updateProjectionPosition("first", 1L);
            verify(projectionService).updateProjectionPosition("second", 1L);
        }

        @Test
        void runAll_null集合_不应抛异常() {
            // runAll 内部使用 CollKit.isEmpty，null 视为空直接返回
            runner.runAll(null);

            verifyNoInteractions(projectionService, chunkReader);
        }

        @Test
        void runAll_空集合_不应抛异常() {
            runner.runAll(List.of());

            verifyNoInteractions(projectionService, chunkReader);
        }

        @Test
        void runAll_其中一个视图抛异常_应向上传播中断后续视图() {
            TestView first = new TestView("first", "first", 0, List.of("created")); // chunkSize 非法
            TestView second = new TestView("second", "second", 100, List.of("created"));

            assertThatThrownBy(() -> runner.runAll(List.of(first, second)))
                    .isInstanceOf(IllegalArgumentException.class);

            // second 不应被执行（无任何 read 调用）
            verify(projectionService, never()).readProjectionPosition(any());
        }

        @Test
        void runAllIsolated_其中一个视图抛异常_应继续执行后续视图() {
            TestView first = new TestView("first", "first", 0, List.of("created"));
            TestView second = new TestView("second", "second", 100, List.of("created"));
            when(projectionService.readProjectionPosition("second")).thenReturn(0L);
            when(chunkReader.read(eq("second"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e2"), 1));

            runner.runAllIsolated(List.of(first, second));

            assertThat(second.handled).containsExactly("e2");
            verify(projectionService).updateProjectionPosition("second", 1L);
        }

        @Test
        void runAll_单视图集合_应等价于runOnce() {
            TestView view = new TestView("person-list", "person-list", 100, List.of("created"));
            when(projectionService.readProjectionPosition("person-list")).thenReturn(0L);
            when(chunkReader.read(eq("person-list"), eq(0L), eq(100), eq(List.of("created"))))
                    .thenReturn(new EventChunk<>(List.of("e1"), 1));

            runner.runAll(List.of(view));

            assertThat(view.handled).containsExactly("e1");
            verify(projectionService).updateProjectionPosition("person-list", 1L);
        }
    }
}
