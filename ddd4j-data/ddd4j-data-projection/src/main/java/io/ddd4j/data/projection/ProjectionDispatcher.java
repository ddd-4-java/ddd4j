package io.ddd4j.data.projection;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionService;
import io.ddd4j.core.ddd.event.DomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
 * <h3>事务边界（与命令侧 ddd4j-data-cqrs-spring 同款）</h3>
 * <p>
 * 本类<b>不包装事务</b>：{@link #dispatchOne} 只保证「先应用、后提交位置」的顺序
 * （handler 抛异常时位置不推进），事务与重试由业务 bean 自管。运行时适配层如需
 * 事务包裹，应在其调度器实现里织入，而非本 SPI。
 *
 * <h3>响应式轨道</h3>
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

    private final ProjectionHandlerRegistry registry;

    private final EventChunkReader<DomainEvent<?>> chunkReader;

    private final ProjectionPositionRepository positions;

    private final ProjectionService service;

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

    private Flux<DomainEvent<?>> pullChunks(Class<?> eventType, ProjectionHandler handler) {
        String streamId = streamIdOf(handler);
        int chunkSize = actualChunkSize(handler);
        Collection<String> eventNames = eventTypeNames(handler);
        return Flux.generate(
                () -> Cursor.start(service.readProjectionPosition(streamId)),
                (cursor, sink) -> pullStep(eventType, streamId, chunkSize, eventNames, cursor, sink));
    }

    /**
     * 单事件顺序应用并推进投影位置（position++ 后提交）。
     *
     * <p>执行顺序固定为「先 {@link ProjectionHandler#handle} 应用、后提交位置」：
     * handler 抛出的异常直接传播给调用者且位置保持不变（不丢事件、可重试）。
     * 当前为同步实现（保持应用顺序），返回 {@link CompletableFuture} 仅为给
     * 各调度器提供统一的异步合同；<b>不包装事务</b>（业务 bean 自管）。
     *
     * @param event   领域事件，非空
     * @param handler 订阅该事件的 handler，非空
     * @return 应用并提交完成后的 future（同步完成）
     * @throws NullPointerException     event 或 handler 为 null
     * @throws RuntimeException         handler 应用失败时原样传播（位置不推进）
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
     * 流式拉取的生成器状态：投影位置 + 当前事件块 + 块内游标。
     */
    private static final class Cursor {
        private final long position;
        private final EventChunk<DomainEvent<?>> chunk;
        private final int index;
        Cursor(long position, EventChunk<DomainEvent<?>> chunk, int index) {
            this.position = position;
            this.chunk = chunk;
            this.index = index;
        }
        public long position() { return position; }
        public EventChunk<DomainEvent<?>> chunk() { return chunk; }
        public int index() { return index; }
        @Override public boolean equals(Object o) { return this == o || (o instanceof Cursor && position == ((Cursor)o).position && java.util.Objects.equals(chunk, ((Cursor)o).chunk) && index == ((Cursor)o).index); }
        @Override public int hashCode() { return java.util.Objects.hash(position, chunk, index); }
        @Override public String toString() { return "Cursor{position=" + position + ", chunk=" + chunk + ", index=" + index + "}"; }

        static Cursor start(long position) {
            return new Cursor(position, null, 0);
        }
    }

    private void commitPosition(String streamId) {
        ProjectionPosition current = positions.findByStreamId(streamId)
                .orElseGet(() -> DefaultProjectionPosition.zero(streamId));
        positions.save(current.withNextEventNumber(current.getNextEventNumber() + 1));
    }

    private String streamIdOf(ProjectionHandler handler) {
        return handler.getName();
    }

    private int actualChunkSize(ProjectionHandler handler) {
        int chunkSize = handler.getChunkSize();
        return chunkSize > 0 ? chunkSize : 100;
    }

    private Collection<String> eventTypeNames(ProjectionHandler handler) {
        return handler.eventTypes().stream()
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toList());
    }
}
