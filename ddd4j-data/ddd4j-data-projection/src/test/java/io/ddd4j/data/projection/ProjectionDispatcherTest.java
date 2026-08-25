package io.ddd4j.data.projection;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionService;
import io.ddd4j.core.ddd.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ProjectionDispatcher} 分发门面契约测试。
 * <p>
 * 用脚本化 {@link EventChunkReader}（可校验读取参数）+ core 的
 * {@link InMemoryProjectionPositionRepository}／{@link DefaultProjectionService}
 * 组装真实依赖（免 mock 容器），守护：按订阅类型过滤的流式拉取、
 * 未注册类型空流兜底、dispatchOne 的「先应用后提交」顺序与 position++ 语义、
 * handler 失败时位置不推进、不包装事务（同步完成 future）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class ProjectionDispatcherTest {

    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    private final InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();

    private final ProjectionService service = new DefaultProjectionService(positions);

    private final ScriptedChunkReader chunkReader = new ScriptedChunkReader();

    private final ProjectionDispatcher dispatcher =
            new ProjectionDispatcher(registry, chunkReader, positions, service);

    @Test
    void chunkByEventEmitsOnlyRequestedTypeAndCompletesWhenExhausted() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        Set.of(ProjectionHandlerRegistryTest.OrderCreated.class,
                                ProjectionHandlerRegistryTest.OrderPaid.class));
        registry.register(handler);
        ProjectionHandlerRegistryTest.OrderCreated first = new ProjectionHandlerRegistryTest.OrderCreated();
        ProjectionHandlerRegistryTest.OrderPaid paid = new ProjectionHandlerRegistryTest.OrderPaid();
        ProjectionHandlerRegistryTest.OrderCreated second = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(List.of(first, paid, second), 3));
        chunkReader.enqueue(EventChunk.empty(3));

        List<DomainEvent<?>> emitted = dispatcher
                .chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)
                .collectList()
                .block();

        assertThat(emitted).containsExactly(first, second);
        assertThat(chunkReader.calls()).hasSize(2);
        ScriptedChunkReader.ReadCall initialCall = chunkReader.calls().get(0);
        assertThat(initialCall.streamId()).isEqualTo("order-summary");
        assertThat(initialCall.fromEventNumber()).isZero();
        assertThat(initialCall.chunkSize()).isEqualTo(100);
        assertThat(initialCall.eventTypes())
                .containsExactlyInAnyOrder("OrderCreated", "OrderPaid");
    }

    @Test
    void chunkByEventReturnsEmptyFluxForUnregisteredEventType() {
        registry.register(new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                Set.of(ProjectionHandlerRegistryTest.OrderCreated.class)));

        List<DomainEvent<?>> emitted = dispatcher
                .chunkByEvent(ProjectionHandlerRegistryTest.OrderShipped.class)
                .collectList()
                .block();

        assertThat(emitted).isEmpty();
        assertThat(chunkReader.calls()).isEmpty();
    }

    @Test
    void chunkByEventContinuesFromPersistedPosition() {
        registry.register(new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                Set.of(ProjectionHandlerRegistryTest.OrderCreated.class)));
        positions.save(new DefaultProjectionPosition("order-summary", 7));

        dispatcher.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)
                .collectList()
                .block();

        assertThat(chunkReader.calls().get(0).fromEventNumber()).isEqualTo(7);
    }

    @Test
    void dispatchOneAppliesHandlerInOrderAndAdvancesPositionByOne() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        registry.register(handler);
        ProjectionHandlerRegistryTest.OrderCreated first = new ProjectionHandlerRegistryTest.OrderCreated();
        ProjectionHandlerRegistryTest.OrderCreated second = new ProjectionHandlerRegistryTest.OrderCreated();
        ProjectionHandlerRegistryTest.OrderCreated third = new ProjectionHandlerRegistryTest.OrderCreated();

        dispatcher.dispatchOne(first, handler).join();
        dispatcher.dispatchOne(second, handler).join();
        dispatcher.dispatchOne(third, handler).join();

        assertThat(handler.handled()).containsExactly(first, second, third);
        assertThat(service.readProjectionPosition("order-summary")).isEqualTo(3L);
        assertThat(positions.findByStreamId("order-summary"))
                .hasValueSatisfying(position -> assertThat(position.getNextEventNumber()).isEqualTo(3L));
    }

    @Test
    void dispatchOneKeepsPositionWhenHandlerFails() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        handler.failWith(new IllegalStateException("projection boom"));
        registry.register(handler);

        assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), handler));

        assertThat(service.readProjectionPosition("order-summary")).isZero();
        assertThat(handler.handled()).isEmpty();
    }

    @Test
    void dispatchOneRejectsNullArguments() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));

        assertThrows(NullPointerException.class,
                () -> dispatcher.dispatchOne(null, handler));
        assertThrows(NullPointerException.class,
                () -> dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), null));
    }

    @Test
    void constructorRejectsNullComponents() {
        EventChunkReader<DomainEvent<?>> reader = new ScriptedChunkReader();

        assertThrows(NullPointerException.class,
                () -> new ProjectionDispatcher(null, reader, positions, service));
        assertThrows(NullPointerException.class,
                () -> new ProjectionDispatcher(registry, null, positions, service));
        assertThrows(NullPointerException.class,
                () -> new ProjectionDispatcher(registry, reader, null, service));
        assertThrows(NullPointerException.class,
                () -> new ProjectionDispatcher(registry, reader, positions, null));
    }

    /**
     * 脚本化事件块读取器：按入队顺序返回事件块，耗尽后返回空块；
     * 同时记录每次读取参数供断言（读取约定：streamId／起始位置／块大小／事件类型名）。
     */
    static final class ScriptedChunkReader implements EventChunkReader<DomainEvent<?>> {

        private final Queue<EventChunk<DomainEvent<?>>> script = new ArrayDeque<>();

        private final List<ReadCall> calls = new ArrayList<>();

        void enqueue(EventChunk<DomainEvent<?>> chunk) {
            script.add(chunk);
        }

        List<ReadCall> calls() {
            return calls;
        }

        @Override
        public EventChunk<DomainEvent<?>> read(String streamId, long fromEventNumber, int chunkSize,
                                               Collection<String> eventTypes) {
            calls.add(new ReadCall(streamId, fromEventNumber, chunkSize, List.copyOf(eventTypes)));
            EventChunk<DomainEvent<?>> chunk = script.poll();
            return chunk != null ? chunk : EventChunk.empty(fromEventNumber);
        }

        record ReadCall(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes) {
        }
    }
}
