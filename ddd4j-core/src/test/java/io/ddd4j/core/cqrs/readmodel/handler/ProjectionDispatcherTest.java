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

    /**
     * 当前用例注册被测组件和观察指标的容器。
     */
    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    /**
     * 测试使用或记录的多条投影位点。
     */
    private final InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();

    /**
     * 当前测试调用或验证生命周期的应用服务。
     */
    private final ProjectionService service = new DefaultProjectionService(positions);

    /**
     * 按位点分块读取事件的投影组件。
     */
    private final ScriptedChunkReader chunkReader = new ScriptedChunkReader();

    /**
     * 供测试执行事件或 HTTP 通知分发的组件。
     */
    private final ProjectionDispatcher dispatcher =
            new ProjectionDispatcher(registry, chunkReader, positions, service);

    /**
     * 验证<code>isolated.asView(handler)</code> 抛出 <code>NullPointerException.class</code>，<code>isolated.runOnce(handler)</code> 抛出 <code>NullPointerException.class</code>，<code>executionFailure</code> 的异常消息为 <code>"event type must not be null"</code>。
     */
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

    /**
     * 验证<code>dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)</code> 抛出 <code>IllegalStateException.class</code>，<code>dispatcher.runOnce(handler)</code> 抛出 <code>IllegalStateException.class</code>，<code>service.readProjectionPosition("first")</code> 等于 <code>10</code>。
     */
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

    /**
     * 验证<code>dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)</code> 抛出 <code>IllegalStateException.class</code>，<code>chunkReader.calls()</code>为空，<code>service.readProjectionPosition("second")</code>为零。
     */
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

    /**
     * 验证<code>view.getName()</code> 等于 <code>"first"</code>，<code>view.getStreamId()</code> 等于 <code>"first"</code>，<code>handler.handled</code> 按顺序仅包含 <code>event</code>。
     */
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
        view.handleEvents(java.util.Arrays.asList(event));
        assertThat(handler.handled).containsExactly(event);
    }

    /**
     * 验证<code>result.getNextEventNumber()</code> 等于 <code>20</code>，<code>service.readProjectionPosition("gap")</code> 等于 <code>20</code>，<code>handler.handled()</code> 按顺序仅包含 <code>first</code>、<code>second</code>。
     */
    @Test
    void sourceAwareRunPreservesGapPositionAndHistory() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("gap", java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
        positions.save(new DefaultProjectionPosition("gap", 10));
        DomainEvent<?> first = new ProjectionHandlerRegistryTest.OrderCreated();
        DomainEvent<?> second = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(java.util.Arrays.asList(first, second), 20));
        EventChunk<DomainEvent<?>> result = dispatcher.runOnce(handler);
        dispatcher.runOnce(handler);
        assertThat(result.getNextEventNumber()).isEqualTo(20);
        assertThat(service.readProjectionPosition("gap")).isEqualTo(20);
        assertThat(handler.handled()).containsExactly(first, second);
        assertThat(chunkReader.calls()).extracting(ScriptedChunkReader.ReadCall::fromEventNumber)
                .containsExactly(10L, 20L);
    }

    /**
     * 验证<code>dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)</code> 抛出 <code>IllegalStateException.class</code>，<code>service.readProjectionPosition("first")</code> 等于 <code>10</code>，<code>service.readProjectionPosition("first")</code> 等于 <code>20</code>。
     */
    @Test
    void failedViewDoesNotLoseOtherSubscribersAndCanResumeAtOwnPosition() {
        ProjectionHandlerRegistryTest.RecordingHandler first =
                new ProjectionHandlerRegistryTest.RecordingHandler("first", java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
        ProjectionHandlerRegistryTest.RecordingHandler second =
                new ProjectionHandlerRegistryTest.RecordingHandler("second", java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
        registry.register(first);
        registry.register(second);
        first.failWith(new IllegalStateException("business write failed"));
        service.updateProjectionPosition("first", 10);
        service.updateProjectionPosition("second", 10);
        DomainEvent<?> event = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(java.util.Arrays.asList(event, event), 20));
        chunkReader.enqueue(new EventChunk<>(java.util.Arrays.asList(event, event), 20));
        assertThrows(IllegalStateException.class,
                () -> dispatcher.runByEvent(ProjectionHandlerRegistryTest.OrderCreated.class));
        assertThat(service.readProjectionPosition("first")).isEqualTo(10);
        assertThat(service.readProjectionPosition("second")).isEqualTo(20);
        assertThat(second.handled()).hasSize(2);
        first.failWith(null);
        chunkReader.enqueue(new EventChunk<>(java.util.Arrays.asList(event, event), 20));
        dispatcher.runOnce(first);
        assertThat(service.readProjectionPosition("first")).isEqualTo(20);
        assertThat(chunkReader.calls()).extracting(ScriptedChunkReader.ReadCall::fromEventNumber)
                .containsExactly(10L, 10L, 10L);
    }

    /**
     * 验证<code>service.readProjectionPosition("legacy")</code> 等于 <code>10</code>，<code>handler.handled()</code>为空，<code>service.readProjectionPosition("legacy")</code> 等于 <code>12</code>。
     */
    @Test
    void legacyBareEventKeepsCountPositionAndReadOnlyStreamDoesNotDispatch() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("legacy", java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
        registry.register(handler);
        service.updateProjectionPosition("legacy", 10);
        DomainEvent<?> event = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(java.util.Arrays.asList(event, event), 20));
        List<DomainEvent<?>> events = dispatcher.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)
                .collectList().block();
        assertThat(service.readProjectionPosition("legacy")).isEqualTo(10);
        assertThat(handler.handled()).isEmpty();
        events.forEach(item -> dispatcher.dispatchOne(item, handler));
        assertThat(service.readProjectionPosition("legacy")).isEqualTo(12);
    }

    /**
     * 验证<code>view.getName()</code> 等于 <code>"adapter"</code>，<code>view.getStreamId()</code> 等于 <code>"adapter"</code>，<code>view.getEventTypes()</code> 按顺序仅包含 <code>"OrderCreated"</code>。
     */
    @Test
    void adapterPreservesHandlerMetadata() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("adapter", java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
        ProjectionView<DomainEvent<?>> view = dispatcher.asView(handler);
        assertThat(view.getName()).isEqualTo("adapter");
        assertThat(view.getStreamId()).isEqualTo("adapter");
        assertThat(view.getCron()).isEqualTo(handler.getCron());
        assertThat(view.getChunkSize()).isEqualTo(handler.getChunkSize());
        assertThat(view.getEventTypes()).containsExactly("OrderCreated");
    }

    /**
     * 验证<code>isolated.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class) .collectList().block()</code>为空，<code>received.get()</code> 按顺序仅包含 <code>"OrderCreated"</code>，<code>received.get().clear()</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void chunkReaderReceivesImmutableEventTypeNames() {
        registry.register(new ProjectionHandlerRegistryTest.RecordingHandler(
                "order-summary", java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class)));
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

    /**
     * 验证<code>emitted</code> 按顺序仅包含 <code>first</code>、<code>second</code>，<code>chunkReader.calls()</code> 的大小为 <code>2</code>，<code>initialCall.eventTypes()</code> 仅包含 <code>"OrderCreated"</code>、<code>"OrderPaid"</code>。
     */
    @Test
    void chunkByEventEmitsOnlyRequestedTypeAndCompletesWhenExhausted() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        new java.util.LinkedHashSet<>(java.util.Arrays.asList(ProjectionHandlerRegistryTest.OrderCreated.class,
                                ProjectionHandlerRegistryTest.OrderPaid.class)));
        registry.register(handler);
        ProjectionHandlerRegistryTest.OrderCreated first = new ProjectionHandlerRegistryTest.OrderCreated();
        ProjectionHandlerRegistryTest.OrderPaid paid = new ProjectionHandlerRegistryTest.OrderPaid();
        ProjectionHandlerRegistryTest.OrderCreated second = new ProjectionHandlerRegistryTest.OrderCreated();
        chunkReader.enqueue(new EventChunk<>(java.util.Arrays.asList(first, paid, second), 3));
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

    /**
     * 验证<code>emitted</code>为空，<code>chunkReader.calls()</code>为空。
     */
    @Test
    void chunkByEventReturnsEmptyFluxForUnregisteredEventType() {
        registry.register(new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class)));

        List<DomainEvent<?>> emitted = dispatcher
                .chunkByEvent(ProjectionHandlerRegistryTest.OrderShipped.class)
                .collectList()
                .block();

        assertThat(emitted).isEmpty();
        assertThat(chunkReader.calls()).isEmpty();
    }

    /**
     * 验证<code>chunkReader.calls().get(0).fromEventNumber()</code> 等于 <code>7</code>。
     */
    @Test
    void chunkByEventContinuesFromPersistedPosition() {
        registry.register(new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class)));
        positions.save(new DefaultProjectionPosition("order-summary", 7));

        dispatcher.chunkByEvent(ProjectionHandlerRegistryTest.OrderCreated.class)
                .collectList()
                .block();

        assertThat(chunkReader.calls().get(0).fromEventNumber()).isEqualTo(7);
    }

    /**
     * 验证<code>handler.handled()</code> 按顺序仅包含 <code>first</code>、<code>second</code>、<code>third</code>，<code>service.readProjectionPosition("order-summary")</code> 等于 <code>3L</code>，<code>position.getNextEventNumber()</code> 等于 <code>3L</code>。
     */
    @Test
    void dispatchOneAppliesHandlerInOrderAndAdvancesPositionByOne() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
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

    /**
     * 验证<code>dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), handler)</code> 抛出 <code>IllegalStateException.class</code>，<code>service.readProjectionPosition("order-summary")</code>为零，<code>handler.handled()</code>为空。
     */
    @Test
    void dispatchOneKeepsPositionWhenHandlerFails() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));
        handler.failWith(new IllegalStateException("projection boom"));
        registry.register(handler);

        assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), handler));

        assertThat(service.readProjectionPosition("order-summary")).isZero();
        assertThat(handler.handled()).isEmpty();
    }

    /**
     * 验证<code>dispatcher.dispatchOne(null, handler)</code> 抛出 <code>NullPointerException.class</code>，<code>dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void dispatchOneRejectsNullArguments() {
        ProjectionHandlerRegistryTest.RecordingHandler handler =
                new ProjectionHandlerRegistryTest.RecordingHandler("order-summary",
                        java.util.Collections.singleton(ProjectionHandlerRegistryTest.OrderCreated.class));

        assertThrows(NullPointerException.class,
                () -> dispatcher.dispatchOne(null, handler));
        assertThrows(NullPointerException.class,
                () -> dispatcher.dispatchOne(new ProjectionHandlerRegistryTest.OrderCreated(), null));
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
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

        /**
         * 当前用例执行的数据库脚本或表达式文本。
         */
        private final Queue<EventChunk<DomainEvent<?>>> script = new ArrayDeque<>();

        /**
         * 记录被测入口实际触发的调用。
         */
        private final List<ReadCall> calls = new ArrayList<>();

        /**
         * 调用 <code>script.add()</code> 执行当前夹具操作。
         *
         * @param chunk 传给 <code>script.add()</code> 的 <code>EventChunk&lt;DomainEvent&lt;?&gt;&gt;</code> 输入
         */
        void enqueue(EventChunk<DomainEvent<?>> chunk) {
            script.add(chunk);
        }

        /**
         * 读取记录被测入口实际触发的调用。
         *
         * @return 记录被测入口实际触发的调用
         */
        List<ReadCall> calls() {
            return calls;
        }

        /**
         * 读取调用指定的数据或事件流。
         *
         * <p>测试夹具会调用 <code>calls.add(new ReadCall(streamId, fromEventNumber, chunkSize, new java.util.ArrayList<>(eventTypes)))</code>、<code>new java.util.ArrayList<>(eventTypes)</code>。</p>
         *
         * @param streamId        投影流 ID
         * @param fromEventNumber 起始事件号，包含该位置
         * @param chunkSize       本次最大读取数量
         * @param eventTypes      需要读取的事件类型；为空表示由实现自行决定
         * @return 实际返回<code>chunk != null ? chunk : EventChunk.empty(fromEventNumber)</code>
         */
        @Override
        public EventChunk<DomainEvent<?>> read(String streamId, long fromEventNumber, int chunkSize,
                                               Collection<String> eventTypes) {
            calls.add(new ReadCall(streamId, fromEventNumber, chunkSize, new java.util.ArrayList<>(eventTypes)));
            EventChunk<DomainEvent<?>> chunk = script.poll();
            return chunk != null ? chunk : EventChunk.empty(fromEventNumber);
        }

        /**
         * 将 <code>serialVersionUID</code>、<code>streamId</code>、<code>fromEventNumber</code>、<code>chunkSize</code> 等状态一起传入当前测试，供调用或结果断言使用。
         */
        final static class ReadCall {

            /**
             * Java 序列化使用的版本标识。
             */
            private static final long serialVersionUID = 0L;

            /**
             * 投影任务使用的事件流标识。
             */
            private final String streamId;

            /**
             * 本次投影测试请求读取的起始事件位点。
             */
            private final long fromEventNumber;

            /**
             * 分批读取事件时配置的单批大小。
             */
            private final int chunkSize;

            /**
             * 夹具向投影运行器声明订阅的事件类型。
             */
            private final Collection<String> eventTypes;

            /**
             * 用给定值初始化 <code>ReadCall</code> 实例。
             *
             * @param streamId        投影任务使用的事件流标识
             * @param fromEventNumber <code>fromEventNumber</code> 字段
             * @param chunkSize       <code>chunkSize</code> 字段
             * @param eventTypes      <code>eventTypes</code> 字段
             */
            @JsonCreator()
            ReadCall(@JsonProperty("streamId") String streamId, @JsonProperty("fromEventNumber") long fromEventNumber, @JsonProperty("chunkSize") int chunkSize, @JsonProperty("eventTypes") Collection<String> eventTypes) {
                this.streamId = streamId;
                this.fromEventNumber = fromEventNumber;
                this.chunkSize = chunkSize;
                this.eventTypes = eventTypes;
            }

            /**
             * 读取投影任务使用的事件流标识。
             *
             * @return 投影任务使用的事件流标识
             */
            @JsonProperty("streamId")
            public String streamId() {
                return streamId;
            }

            /**
             * 读取<code>fromEventNumber</code> 字段。
             *
             * @return <code>fromEventNumber</code> 字段
             */
            @JsonProperty("fromEventNumber")
            public long fromEventNumber() {
                return fromEventNumber;
            }

            /**
             * 读取<code>chunkSize</code> 字段。
             *
             * @return <code>chunkSize</code> 字段
             */
            @JsonProperty("chunkSize")
            public int chunkSize() {
                return chunkSize;
            }

            /**
             * 读取<code>eventTypes</code> 字段。
             *
             * @return <code>eventTypes</code> 字段
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
