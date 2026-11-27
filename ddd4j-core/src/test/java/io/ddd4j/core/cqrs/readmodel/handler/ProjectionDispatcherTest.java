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
package io.ddd4j.core.cqrs.readmodel.handler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.ddd.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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

    /** \u5f53\u524d\u7528\u4f8b\u6ce8\u518c\u88ab\u6d4b\u7ec4\u4ef6\u548c\u89c2\u5bdf\u6307\u6807\u7684\u5bb9\u5668\u3002 */
    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
    private final InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();

    /** \u5f53\u524d\u6d4b\u8bd5\u8c03\u7528\u6216\u9a8c\u8bc1\u751f\u547d\u5468\u671f\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final ProjectionService service = new DefaultProjectionService(positions);

    /** \u6309\u4f4d\u70b9\u5206\u5757\u8bfb\u53d6\u4e8b\u4ef6\u7684\u6295\u5f71\u7ec4\u4ef6\u3002 */
    private final ScriptedChunkReader chunkReader = new ScriptedChunkReader();

    /** \u4f9b\u6d4b\u8bd5\u6267\u884c\u4e8b\u4ef6\u6216 HTTP \u901a\u77e5\u5206\u53d1\u7684\u7ec4\u4ef6\u3002 */
    private final ProjectionDispatcher dispatcher =
            new ProjectionDispatcher(registry, chunkReader, positions, service);

    /** \u9a8c\u8bc1<code>isolated.asView(handler)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>isolated.runOnce(handler)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>executionFailure</code> \u7684\u5f02\u5e38\u6d88\u606f\u4e3a <code>"event type must not be null"</code>\u3002 */
    @Test
    void invalidDirectHandlerIsRejectedBeforeReadHandleOrCheckpointActivity() {
        ProjectionHandlerRegistryTest.MutableHandler handler = new ProjectionHandlerRegistryTest.MutableHandler();
        handler.types.add(null);
        List<String> checkpointActivity = new ArrayList<>();
        ProjectionPositionRepository observedPositions = new InMemoryProjectionPositionRepository() {
            @Override
            public Optional<ProjectionPosition> findByStreamId(String streamId) {
                checkpointActivity.add("read");
                return super.findByStreamId(streamId);
            }

            @Override
            public ProjectionPosition save(ProjectionPosition position) {
                checkpointActivity.add("save");
                return super.save(position);
            }
        };
        ProjectionDispatcher isolated = new ProjectionDispatcher(registry, chunkReader, observedPositions,
                new DefaultProjectionService(observedPositions));

        NullPointerException adaptationFailure = assertThrows(NullPointerException.class,
                () -> isolated.asView(handler));
        NullPointerException executionFailure = assertThrows(NullPointerException.class,
                () -> isolated.runOnce(handler));

        assertThat(chunkReader.calls()).isEmpty();
        assertThat(handler.handled).isEmpty();
        assertThat(checkpointActivity).isEmpty();
        assertThat(adaptationFailure).hasMessage("event type must not be null");
        assertThat(executionFailure).hasMessage("event type must not be null");
    }

    /** \u9a8c\u8bc1<code>dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>dispatcher.runOnce(handler)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>service.readProjectionPosition("first")</code> \u7b49\u4e8e <code>10</code>\u3002 */
    @Test
    void changedRegisteredSubscriptionsFailBeforeReadHandleOrCheckpointAdvance() {
        ProjectionHandlerRegistryTest.MutableHandler handler = new ProjectionHandlerRegistryTest.MutableHandler();
        registry.register(handler);
        service.updateProjectionPosition("first", 10);
        handler.types.clear();
        handler.types.add(ProjectionHandlerRegistryTest.OrderPaid.class);
        chunkReader.enqueue(EventChunk.empty(20));

        assertThrows(IllegalStateException.class,
                () -> dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class));
        assertThrows(IllegalStateException.class, () -> dispatcher.runOnce(handler));
        assertThat(chunkReader.calls()).isEmpty();
        assertThat(handler.handled).isEmpty();
        assertThat(service.readProjectionPosition("first")).isEqualTo(10);
    }

    /** \u9a8c\u8bc1<code>dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>chunkReader.calls()</code>\u4e3a\u7a7a\uff0c<code>service.readProjectionPosition("second")</code>\u4e3a\u96f6\u3002 */
    @Test
    void changedRegisteredNameFailsBeforeReadingAnotherCheckpoint() {
        ProjectionHandlerRegistryTest.MutableHandler handler = new ProjectionHandlerRegistryTest.MutableHandler();
        registry.register(handler);
        service.updateProjectionPosition("first", 10);
        handler.name = "second";
        chunkReader.enqueue(EventChunk.empty(20));

        assertThrows(IllegalStateException.class,
                () -> dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class));
        assertThat(chunkReader.calls()).isEmpty();
        assertThat(handler.handled).isEmpty();
        assertThat(service.readProjectionPosition("first")).isEqualTo(10);
        assertThat(service.readProjectionPosition("second")).isZero();
    }

    /** \u9a8c\u8bc1<code>view.getName()</code> \u7b49\u4e8e <code>"first"</code>\uff0c<code>view.getStreamId()</code> \u7b49\u4e8e <code>"first"</code>\uff0c<code>handler.handled</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>event</code>\u3002 */
    @Test
    void directlyAdaptedViewFreezesIdentityAndSubscriptionAtAdaptation() {
        ProjectionHandlerRegistryTest.MutableHandler handler = new ProjectionHandlerRegistryTest.MutableHandler();
        ProjectionView<DomainEvent<?>> view = dispatcher.asView(handler);
        handler.name = "second";
        handler.types.clear();
        handler.types.add(ProjectionHandlerRegistryTest.OrderPaid.class);

        assertThat(view.getName()).isEqualTo("first");
        assertThat(view.getStreamId()).isEqualTo("first");
        assertThat(view.getEventTypes()).containsExactly("OrderCreated");
        DomainEvent<?> event = new ProjectionHandlerRegistryTest.OrderCreated();
        view.handleEvents(List.of(event));
        assertThat(handler.handled).containsExactly(event);
    }

    /** \u9a8c\u8bc1<code>result.getNextEventNumber()</code> \u7b49\u4e8e <code>20</code>\uff0c<code>service.readProjectionPosition("gap")</code> \u7b49\u4e8e <code>20</code>\uff0c<code>handler.handled()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>first</code>\u3001<code>second</code>\u3002 */
    @Test
    void sourceAwareRunPreservesGapPositionAndHistory() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("gap", Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        positions.save(new DefaultProjectionPosition("gap", 10));
        DomainEvent<?> first = new ProjectionHandlerRegistryTest.OrderCreated();
        DomainEvent<?> second = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(List.of(first, second), 20));
        EventChunk<DomainEvent<?>> result = dispatcher.runOnce(handler);
        dispatcher.runOnce(handler);
        assertThat(result.getNextEventNumber()).isEqualTo(20);
        assertThat(service.readProjectionPosition("gap")).isEqualTo(20);
        assertThat(handler.handled()).containsExactly(first, second);
        assertThat(chunkReader.calls()).extracting(ScriptedChunkReader.ReadCall::fromEventNumber)
                .containsExactly(10L, 20L);
    }

    /** \u9a8c\u8bc1<code>dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>service.readProjectionPosition("first")</code> \u7b49\u4e8e <code>10</code>\uff0c<code>service.readProjectionPosition("first")</code> \u7b49\u4e8e <code>20</code>\u3002 */
    @Test
    void failedViewDoesNotLoseOtherSubscribersAndCanResumeAtOwnPosition() {
        ProjectionHandlerRegistryTest.RecordingHandler first =
                new ProjectionHandlerRegistryTest.RecordingHandler("first", Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        ProjectionHandlerRegistryTest.RecordingHandler second =
                new ProjectionHandlerRegistryTest.RecordingHandler("second", Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        registry.register(first);
        registry.register(second);
        first.failWith(new IllegalStateException("business write failed"));
        service.updateProjectionPosition("first", 10);
        service.updateProjectionPosition("second", 10);
        DomainEvent<?> event = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(List.of(event, event), 20));
        chunkReader.enqueue(new EventChunk<>(List.of(event, event), 20));
        assertThrows(IllegalStateException.class,
                () -> dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class));
        assertThat(service.readProjectionPosition("first")).isEqualTo(10);
        assertThat(service.readProjectionPosition("second")).isEqualTo(20);
        assertThat(second.handled()).hasSize(2);
        first.failWith(null);
        chunkReader.enqueue(new EventChunk<>(List.of(event, event), 20));
        dispatcher.runOnce(first);
        assertThat(service.readProjectionPosition("first")).isEqualTo(20);
        assertThat(chunkReader.calls()).extracting(ScriptedChunkReader.ReadCall::fromEventNumber)
                .containsExactly(10L, 10L, 10L);
    }

    /** \u9a8c\u8bc1<code>service.readProjectionPosition("legacy")</code> \u7b49\u4e8e <code>10</code>\uff0c<code>handler.handled()</code>\u4e3a\u7a7a\uff0c<code>service.readProjectionPosition("legacy")</code> \u7b49\u4e8e <code>12</code>\u3002 */
    @Test
    void legacyBareEventKeepsCountPositionAndReadOnlyStreamDoesNotDispatch() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("legacy", Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        registry.register(handler);
        service.updateProjectionPosition("legacy", 10);
        DomainEvent<?> event = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(List.of(event, event), 20));
        List<DomainEvent<?>> events = dispatcher.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)
                .collectList().block();
        assertThat(service.readProjectionPosition("legacy")).isEqualTo(10);
        assertThat(handler.handled()).isEmpty();
        events.forEach(item -> dispatcher.dispatchOne(item, handler));
        assertThat(service.readProjectionPosition("legacy")).isEqualTo(12);
    }

    /** \u9a8c\u8bc1<code>view.getName()</code> \u7b49\u4e8e <code>"adapter"</code>\uff0c<code>view.getStreamId()</code> \u7b49\u4e8e <code>"adapter"</code>\uff0c<code>view.getEventTypes()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"OrderCreated"</code>\u3002 */
    @Test
    void adapterPreservesHandlerMetadata() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("adapter", Set.of(ProjectionHandlerRegistryTest.OrderCreated.class));
        ProjectionView<DomainEvent<?>> view = dispatcher.asView(handler);
        assertThat(view.getName()).isEqualTo("adapter");
        assertThat(view.getStreamId()).isEqualTo("adapter");
        assertThat(view.getCron()).isEqualTo(handler.getCron());
        assertThat(view.getChunkSize()).isEqualTo(handler.getChunkSize());
        assertThat(view.getEventTypes()).containsExactly("OrderCreated");
    }

    /** \u9a8c\u8bc1<code>isolated.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class) .collectList().block()</code>\u4e3a\u7a7a\uff0c<code>received.get()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"OrderCreated"</code>\uff0c<code>received.get().clear()</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void chunkReaderReceivesImmutableEventTypeNames() {
        registry.register(new ProjectionHandlerRegistryTest.RecordingHandler(
                "order-summary", Set.of(ProjectionHandlerRegistryTest.OrderCreated.class)));
        AtomicReference<Collection<String>> received = new AtomicReference<>();
        EventChunkReader<DomainEvent<?>> reader = (streamId, fromEventNumber, chunkSize, eventTypes) -> {
            received.set(eventTypes);
            return EventChunk.empty(fromEventNumber);
        };
        ProjectionDispatcher isolated = new ProjectionDispatcher(registry, reader, positions, service);

        assertThat(isolated.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)
                .collectList().block()).isEmpty();
        assertThat(received.get()).containsExactly("OrderCreated");
        assertThrows(UnsupportedOperationException.class, () -> received.get().clear());
    }

    /** \u9a8c\u8bc1<code>emitted</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>first</code>\u3001<code>second</code>\uff0c<code>chunkReader.calls()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>initialCall.eventTypes()</code> \u4ec5\u5305\u542b <code>"OrderCreated"</code>\u3001<code>"OrderPaid"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>emitted</code>\u4e3a\u7a7a\uff0c<code>chunkReader.calls()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>chunkReader.calls().get(0).fromEventNumber()</code> \u7b49\u4e8e <code>7</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>handler.handled()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>first</code>\u3001<code>second</code>\u3001<code>third</code>\uff0c<code>service.readProjectionPosition("order-summary")</code> \u7b49\u4e8e <code>3L</code>\uff0c<code>position.getNextEventNumber()</code> \u7b49\u4e8e <code>3L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), handler)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>service.readProjectionPosition("order-summary")</code>\u4e3a\u96f6\uff0c<code>handler.handled()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>dispatcher.dispatchOne(null, handler)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
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

        /** \u5f53\u524d\u7528\u4f8b\u6267\u884c\u7684\u6570\u636e\u5e93\u811a\u672c\u6216\u8868\u8fbe\u5f0f\u6587\u672c\u3002 */
        private final Queue<EventChunk<DomainEvent<?>>> script = new ArrayDeque<>();

        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        private final List<ReadCall> calls = new ArrayList<>();

        /**
         * \u8c03\u7528 <code>script.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param chunk \u4f20\u7ed9 <code>script.add()</code> \u7684 <code>EventChunk&lt;DomainEvent&lt;?&gt;&gt;</code> \u8f93\u5165
         */
        void enqueue(EventChunk<DomainEvent<?>> chunk) {
            script.add(chunk);
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002
         * @return \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528
         */
        List<ReadCall> calls() {
            return calls;
        }

        /**
         * 读取调用指定的数据或事件流。
         *
         * <p>测试夹具会调用 <code>calls.add(new ReadCall(streamId, fromEventNumber, chunkSize, List.copyOf(eventTypes)))</code>、<code>List.copyOf(eventTypes)</code>。</p>
         *
         * @param streamId 投影流 ID
         * @param fromEventNumber 起始事件号，包含该位置
         * @param chunkSize 本次最大读取数量
         * @param eventTypes 需要读取的事件类型；为空表示由实现自行决定
         * @return 实际返回<code>chunk != null ? chunk : EventChunk.empty(fromEventNumber)</code>
         */
        @Override
        public EventChunk<DomainEvent<?>> read(String streamId, long fromEventNumber, int chunkSize,
                                               Collection<String> eventTypes) {
            calls.add(new ReadCall(streamId, fromEventNumber, chunkSize, List.copyOf(eventTypes)));
            EventChunk<DomainEvent<?>> chunk = script.poll();
            return chunk != null ? chunk : EventChunk.empty(fromEventNumber);
        }

        /** \u5c06 <code>serialVersionUID</code>\u3001<code>streamId</code>\u3001<code>fromEventNumber</code>\u3001<code>chunkSize</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
        final static class ReadCall {

            /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
            private static final long serialVersionUID = 0L;

            /** \u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6\u3002 */
            private final String streamId;

            /** \u672c\u6b21\u6295\u5f71\u6d4b\u8bd5\u8bf7\u6c42\u8bfb\u53d6\u7684\u8d77\u59cb\u4e8b\u4ef6\u4f4d\u70b9\u3002 */
            private final long fromEventNumber;

            /** \u5206\u6279\u8bfb\u53d6\u4e8b\u4ef6\u65f6\u914d\u7f6e\u7684\u5355\u6279\u5927\u5c0f\u3002 */
            private final int chunkSize;

            /** \u5939\u5177\u5411\u6295\u5f71\u8fd0\u884c\u5668\u58f0\u660e\u8ba2\u9605\u7684\u4e8b\u4ef6\u7c7b\u578b\u3002 */
            private final Collection<String> eventTypes;

            /**
             * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>ReadCall</code> \u5b9e\u4f8b\u3002
             * @param streamId \u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6
             * @param fromEventNumber <code>fromEventNumber</code> \u5b57\u6bb5
             * @param chunkSize <code>chunkSize</code> \u5b57\u6bb5
             * @param eventTypes <code>eventTypes</code> \u5b57\u6bb5
             */
            @JsonCreator()
            ReadCall(@JsonProperty("streamId") String streamId, @JsonProperty("fromEventNumber") long fromEventNumber, @JsonProperty("chunkSize") int chunkSize, @JsonProperty("eventTypes") Collection<String> eventTypes) {
                this.streamId = streamId;
                this.fromEventNumber = fromEventNumber;
                this.chunkSize = chunkSize;
                this.eventTypes = eventTypes;
            }

            /**
             * \u8bfb\u53d6\u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6\u3002
             * @return \u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6
             */
            @JsonProperty("streamId")
            public String streamId() {
                return streamId;
            }

            /**
             * \u8bfb\u53d6<code>fromEventNumber</code> \u5b57\u6bb5\u3002
             * @return <code>fromEventNumber</code> \u5b57\u6bb5
             */
            @JsonProperty("fromEventNumber")
            public long fromEventNumber() {
                return fromEventNumber;
            }

            /**
             * \u8bfb\u53d6<code>chunkSize</code> \u5b57\u6bb5\u3002
             * @return <code>chunkSize</code> \u5b57\u6bb5
             */
            @JsonProperty("chunkSize")
            public int chunkSize() {
                return chunkSize;
            }

            /**
             * \u8bfb\u53d6<code>eventTypes</code> \u5b57\u6bb5\u3002
             * @return <code>eventTypes</code> \u5b57\u6bb5
             */
            @JsonProperty("eventTypes")
            public Collection<String> eventTypes() {
                return eventTypes;
            }

            /**
             * 根据<code>streamId</code>、<code>eventTypes</code>、<code>fromEventNumber</code>、<code>chunkSize</code>比较对象的值是否相等。
             *
             * <p>只比较运行时类型相同的对象。</p>
             *
             * @param obj 用来比较或转换的输入对象
             * @return 对象满足此实现的相等条件时为真
             */
            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                    return false;
                }
                ReadCall other = (ReadCall) obj;
                return Objects.equals(this.streamId, other.streamId) && this.fromEventNumber == other.fromEventNumber && this.chunkSize == other.chunkSize && Objects.equals(this.eventTypes, other.eventTypes);
            }

            /**
             * 计算与对象相等条件一致的哈希值。
             *
             * @return 由参与相等比较的状态计算出的哈希值
             */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(streamId);
                result = 31 * result + Long.hashCode(fromEventNumber);
                result = 31 * result + Integer.hashCode(chunkSize);
                result = 31 * result + Objects.hashCode(eventTypes);
                return result;
            }

            /**
             * 生成用于调试和日志的对象描述，包含<code>streamId</code>、<code>fromEventNumber</code>、<code>chunkSize</code>、<code>eventTypes</code>。
             *
             * @return 表示当前对象状态的字符串
             */
            @Override
            public String toString() {
                return "ReadCall[streamId=" + streamId + ", fromEventNumber=" + fromEventNumber + ", chunkSize=" + chunkSize + ", eventTypes=" + eventTypes + "]";
            }
        }
    }
}
