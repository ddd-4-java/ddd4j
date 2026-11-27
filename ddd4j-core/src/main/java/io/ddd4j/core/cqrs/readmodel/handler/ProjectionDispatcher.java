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

import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.ddd.event.DomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 投影分发门面（core 投影契约之上的薄封装）。
 * <p>
 * 把「按 handler 订阅类型拉事件块」与「单事件顺序应用 + 投影位置推进」两个动作
 * 从运行时调度器（阶段 7 Task 7.7+ 的七套 {@code ViewScheduler} 适配）中剥离出来，
 * 调度器只需要按 {@link ProjectionHandler#getCron()} 触发并复用本类——
 * 事件读取经 core {@link EventChunkReader}，位置提交经 core
 * {@link ProjectionPositionRepository}／{@link ProjectionService}，
 * 本类<b>不重定义任何 core 投影抽象</b>，也不做类型路由之外的分发决策
 * （多事件类型的字符串路由已有 core {@code TypedEventDispatcher} 承担）。
 *
 * <h2>事务边界（与命令侧 ddd4j-data-cqrs-spring 同款）</h2>
 * <p>
 * 本类<b>不包装事务</b>：{@link #dispatchOne} 只保证「先应用、后提交位置」的顺序
 * （handler 抛异常时位置不推进），事务与重试由业务 bean 自管。运行时适配层如需
 * 事务包裹，应在其调度器实现里织入，而非本 SPI。
 *
 * <h2>响应式轨道</h2>
 * <p>
 * {@link #chunkByEvent(Class)} 返回 Reactor {@link Flux}（版本由
 * ddd4j-dependencies 的 reactor-bom 管理）——与 ddd4j-data-event-store 的
 * {@code AsyncEventStore} 单轨决策（ADR-0005）同源约定：异步扩展仅此一份
 * Reactor 签名，接受 Reactor 进入 SPI 层；同步调度器可直接
 * {@code flux.toIterable()} 消费。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionHandler
 * @see ProjectionHandlerRegistry
 * @since 2.0.x
 */
public class ProjectionDispatcher {

    /**
     * 读取投影处理器及事件订阅的注册表。
     */
    private final ProjectionHandlerRegistry registry;

    /**
     * 按位点分块读取事件的投影组件。
     */
    private final EventChunkReader<DomainEvent<?>> chunkReader;

    /**
     * 读取并保存投影消费位置的仓储。
     */
    private final ProjectionPositionRepository positions;

    /**
     * 读取事件和推进投影检查点的服务。
     */
    private final ProjectionService service;

    /**
     * 执行单次分批投影任务的运行器。
     */
    private final ProjectionRunner<DomainEvent<?>> runner;

    /**
     * 构造分发门面。
     *
     * <p>{@code service} 承担位置读取（{@link ProjectionService#readProjectionPosition}），
     * {@code positions} 承担 {@link #dispatchOne} 的位置提交写入——两者应由适配层
     * 指向同一份位置存储（core {@code DefaultProjectionService} 即为
     * {@code ProjectionPositionRepository} 的默认包装）。
     *
     * @param registry    handler 注册中心，非空
     * @param chunkReader 事件块读取器，非空（core SPI，由事件存储适配层提供）
     * @param positions   投影位置仓储，非空（core SPI）
     * @param service     投影位置服务，非空（core SPI）
     * @throws NullPointerException 任一参数为 null
     */
    public ProjectionDispatcher(ProjectionHandlerRegistry registry,
                                EventChunkReader<DomainEvent<?>> chunkReader,
                                ProjectionPositionRepository positions,
                                ProjectionService service) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.chunkReader = Objects.requireNonNull(chunkReader, "chunkReader must not be null");
        this.positions = Objects.requireNonNull(positions, "positions must not be null");
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.runner = new ProjectionRunner<>(this.service, this.chunkReader);
    }

    /**
     * 拉取指定事件类型的增量事件流（按 handler 订阅类型过滤）。
     *
     * <p>以 handler 视图名（{@link ProjectionHandler#getName()}，与 core
     * {@code ProjectionView} 的「streamId 默认取视图名」约定一致）定位投影流，
     * 从 {@link ProjectionService} 记录的位置开始，按
     * {@link ProjectionHandler#getChunkSize()} 分块经 {@link EventChunkReader}
     * 拉取（读取时以 handler 声明的全部事件类型名预过滤——与 core
     * {@code DomainEvent#getEventType()} 的默认派生一致取简单类名），
     * 只下发与 {@code eventType} 匹配的事件；事件耗尽或位置不再前进即完成。
     *
     * <p>本方法只读不写位置（位置推进由 {@link #dispatchOne} 逐事件提交）；
     * 未注册任何 handler 订阅该类型时返回空流（兜底策略在此处决定：
     * 静默跳过，由调度器决定是否告警）。流为懒加载——订阅时才发起读取。
     *
     * @param eventType 事件类型，非空
     * @param <E>       事件类型泛型
     * @return 过滤后的领域事件流（无订阅者时为空流）
     * @throws NullPointerException eventType 为 null
     */
    public <E extends DomainEvent<?>> Flux<DomainEvent<?>> chunkByEvent(Class<E> eventType) {
        Class<E> type = Objects.requireNonNull(eventType, "eventType must not be null");
        return registry.findHandler(type)
                .map(handler -> pullChunks(type, handler))
                .orElseGet(Flux::empty);
    }

    /**
     * 从处理器自己的检查点拉取并处理一个完整源事件块。
     * 成功后按源块 nextEventNumber 提交；失败保留原位置，整块可能重投。
     * 处理顺序、位置推进、重入保护均由共享 Runner 执行。此入口使用 service 读写位置。
     *
     * @param handler 独立视图处理器
     * @return 源返回的完整事件块（保留真实源位置）
     */
    public EventChunk<DomainEvent<?>> runOnce(ProjectionHandler handler) {
        return runner.runOnce(asView(handler));
    }

    /**
     * 对订阅该事件的每个独立视图运行一块，单个失败不阻止其余视图，最后传播首个失败。
     * 每个视图读取其全部订阅类型，避免共享检查点跳过其他订阅事件。
     *
     * @param eventType 用于选择订阅视图的事件类型
     * @param <E>       事件类型
     */
    public <E extends DomainEvent<?>> void runByEvent(Class<E> eventType) {
        RuntimeException firstFailure = null;
        for (ProjectionHandler handler : registry.findHandlers(eventType)) {
            try {
                runOnce(handler);
            } catch (RuntimeException failure) {
                if (Objects.isNull(firstFailure)) {
                    firstFailure = failure;
                } else if (firstFailure != failure) {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        if (Objects.nonNull(firstFailure)) {
            throw firstFailure;
        }
    }

    /**
     * 将旧 Handler 入口适配为框架无关 View，固定本次名称、CRON、块大小和类型声明。
     * 已注册处理器的身份或订阅漂移时，在读取、处理和提交之前拒绝适配。
     *
     * @param handler 业务处理器
     * @return 顺序处理块内事件的视图
     */
    public ProjectionView<DomainEvent<?>> asView(ProjectionHandler handler) {
        ProjectionHandler actual = Objects.requireNonNull(handler, "handler must not be null");
        String name = actual.getName();
        List<Class<? extends DomainEvent<?>>> types = new ArrayList<>(actual.eventTypes());
        for (Class<?> type : types) {
            Objects.requireNonNull(type, "event type must not be null");
        }
        registry.validateMetadata(actual, name, types);
        Collection<String> names = Collections.unmodifiableList(types.stream()
                .map(Class::getSimpleName).collect(Collectors.toList()));
        String cron = actual.getCron();
        int chunkSize = actualChunkSize(actual);
        return new ProjectionView<DomainEvent<?>>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCron() {
                return cron;
            }

            @Override
            public int getChunkSize() {
                return chunkSize;
            }

            @Override
            public Collection<String> getEventTypes() {
                return names;
            }

            @Override
            public void handleEvents(Collection<DomainEvent<?>> events) {
                for (DomainEvent<?> event : events) {
                    actual.handle(event);
                }
            }
        };
    }

    /**
     * 从当前检查点开始按处理器订阅的事件类型分批读取。
     *
     * @param eventType 事件的 Java 类型或持久化类型名称
     * @param handler   当前执行的投影处理器
     * @return 交给旧兼容投影入口的事件流
     */
    private Flux<DomainEvent<?>> pullChunks(Class<?> eventType, ProjectionHandler handler) {
        String streamId = streamIdOf(handler);
        int chunkSize = actualChunkSize(handler);
        Collection<String> eventNames = eventTypeNames(handler);
        return Flux.generate(
                () -> Cursor.start(service.readProjectionPosition(streamId)),
                (cursor, sink) -> pullStep(eventType, streamId, chunkSize, eventNames, cursor, sink));
    }

    /**
     * 旧单事件兼容入口：顺序应用并按消费数量推进位置（position++ 后提交）。
     * <p>裸事件没有源位置，不能用于有过滤间隙的全局事件流；源位置安全入口为
     * {@link #runOnce(ProjectionHandler)}。保留先处理、后从 positions 读取并提交的历史顺序，
     * 该兼容分支不具备 Runner 的源块恢复或重入保证。
     *
     * <p>执行顺序固定为「先 {@link ProjectionHandler#handle} 应用、后提交位置」：
     * handler 抛出的异常直接传播给调用者且位置保持不变（不丢事件、可重试）。
     * 当前为同步实现（保持应用顺序），返回 {@link CompletableFuture} 仅为给
     * 各调度器提供统一的异步合同；<b>不包装事务</b>（业务 bean 自管）。
     *
     * @param event   领域事件，非空
     * @param handler 订阅该事件的 handler，非空
     * @return 应用并提交完成后的 future（同步完成）
     * @throws NullPointerException event 或 handler 为 null
     * @throws RuntimeException     handler 应用失败时原样传播（位置不推进）
     */
    public CompletableFuture<Void> dispatchOne(DomainEvent<?> event, ProjectionHandler handler) {
        DomainEvent<?> actualEvent = Objects.requireNonNull(event, "event must not be null");
        ProjectionHandler actualHandler = Objects.requireNonNull(handler, "handler must not be null");
        actualHandler.handle(actualEvent);
        commitPosition(streamIdOf(actualHandler));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 生成器单步推进：先在当前块内游标后移找下一个匹配事件（每个生成步只发一个
     * {@code next}——{@link SynchronousSink} 契约），当前块扫尽再拉下一块；
     * 事件耗尽或位置不再前进即 {@code complete}。
     *
     * @param eventType  事件负载的 Java 类型
     * @param streamId   投影事件流标识
     * @param chunkSize  单批最多请求的事件数量
     * @param eventNames 订阅的事件名称
     * @param cursor     本次拉取使用的流游标
     * @param sink       接收拉取事件的下游
     * @return 本次分批拉取后的游标状态
     */
    private Cursor pullStep(Class<?> eventType, String streamId, int chunkSize, Collection<String> eventNames,
                            Cursor cursor, SynchronousSink<DomainEvent<?>> sink) {
        Cursor current = cursor;
        while (true) {
            EventChunk<DomainEvent<?>> chunk = current.chunk();
            if (Objects.isNull(chunk) || current.index() >= chunk.getEvents().size()) {
                EventChunk<DomainEvent<?>> next = chunkReader.read(streamId, current.position(), chunkSize, eventNames);
                EventChunk<DomainEvent<?>> safeChunk =
                        Objects.requireNonNull(next, "chunkReader must not return null");
                if (!safeChunk.hasEvents() || safeChunk.getNextEventNumber() <= current.position()) {
                    sink.complete();
                    return current;
                }
                current = new Cursor(safeChunk.getNextEventNumber(), safeChunk, 0);
                continue;
            }
            List<DomainEvent<?>> events = chunk.getEvents();
            for (int index = current.index(); index < events.size(); index++) {
                DomainEvent<?> event = events.get(index);
                if (eventType.isInstance(event)) {
                    sink.next(event);
                    return new Cursor(current.position(), chunk, index + 1);
                }
            }
            current = new Cursor(current.position(), null, 0);
        }
    }

    /**
     * 在旧的逐事件投影入口中将当前位置推进一个序号。
     *
     * @param streamId 投影事件流标识
     */
    private void commitPosition(String streamId) {
        ProjectionPosition current = positions.findByStreamId(streamId)
                .orElseGet(() -> DefaultProjectionPosition.zero(streamId));
        positions.save(current.withNextEventNumber(current.getNextEventNumber() + 1));
    }

    /**
     * 使用投影处理器名称作为事件流标识。
     *
     * @param handler 当前执行的投影处理器
     * @return 依处理器注册的事件流标识
     */
    private String streamIdOf(ProjectionHandler handler) {
        return handler.getName();
    }

    /**
     * 选择处理器声明的正批量大小，缺省时取 100。
     *
     * @param handler 当前执行的投影处理器
     * @return 每次读取的最大事件数
     */
    private int actualChunkSize(ProjectionHandler handler) {
        int chunkSize = handler.getChunkSize();
        return chunkSize > 0 ? chunkSize : 100;
    }

    /**
     * 将处理器订阅的事件类型转换成简单类名。
     *
     * @param handler 当前执行的投影处理器
     * @return 不可变的事件名称集合
     */
    private Collection<String> eventTypeNames(ProjectionHandler handler) {
        return Collections.unmodifiableList(handler.eventTypes().stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toList()));
    }

    /**
     * 流式拉取的生成器状态：投影位置 + 当前事件块 + 块内游标。
     */
    private static final class Cursor {
        /**
         * 尚未交给下游读取的源事件位置。
         */
        private final long position;
        /**
         * 当前从源位置读取的事件批次。
         */
        private final EventChunk<DomainEvent<?>> chunk;
        /**
         * 当前批次中下一条要处理的事件索引。
         */
        private final int index;

        /**
         * 构造指向指定源位置及批次偏移的拉取游标。
         *
         * @param position 当前源事件位置
         * @param chunk    已读取的批次，首次读取前为空
         * @param index    批次中即将处理的事件索引
         */
        Cursor(long position, EventChunk<DomainEvent<?>> chunk, int index) {
            this.position = position;
            this.chunk = chunk;
            this.index = index;
        }

        /**
         * 为指定源位置创建尚未取得任何事件批次的游标。
         *
         * @param position 事件源中的起始位置
         * @return 用来发起第一次拉取的游标
         */
        static Cursor start(long position) {
            return new Cursor(position, null, 0);
        }

        /**
         * 读取<code>position</code> 字段。
         *
         * @return <code>position</code> 字段
         */
        public long position() {
            return position;
        }

        /**
         * 读取<code>chunk</code> 字段。
         *
         * @return <code>chunk</code> 字段
         */
        public EventChunk<DomainEvent<?>> chunk() {
            return chunk;
        }

        /**
         * 读取<code>index</code> 字段。
         *
         * @return <code>index</code> 字段
         */
        public int index() {
            return index;
        }

        /**
         * 根据<code>chunk</code>、<code>Cursor</code>、<code>index</code>比较对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Cursor && position == ((Cursor) o).position && Objects.equals(chunk, ((Cursor) o).chunk) && index == ((Cursor) o).index);
        }

        /**
         * 用<code>position</code>、<code>chunk</code>、<code>index</code>计算哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(position, chunk, index);
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>position</code>、<code>chunk</code>、<code>index</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "Cursor{position=" + position + ", chunk=" + chunk + ", index=" + index + "}";
        }
    }
}
