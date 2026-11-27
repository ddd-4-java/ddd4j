package io.ddd4j.vertx.cqrs;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.ddd4j.vertx.command.VertxCommandBus;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 共享命令/位置契约与实际 Vert.x timer、worker、上下文、生命周期边界。
 */
class VertxCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {
    /**
     * \u6784\u9020 <code>ProjectionView&lt;&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param cron \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @param handler \u5904\u7406\u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u6216\u4e8b\u4ef6\u7684\u7ec4\u4ef6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;Object&gt;</code> \u7ed3\u679c
     */
    private static ProjectionView<Object> view(String name, String cron, Runnable handler) {
        return new ProjectionView<>() {
            public String getName() {
                return name;
            }

            public String getCron() {
                return cron;
            }

            public Collection<String> getEventTypes() {
                return List.of("Order");
            }

            public void handleEvents(Collection<Object> events) {
                handler.run();
            }
        };
    }

    /**
     * \u901a\u8fc7 <code>result.toCompletionStage()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param result \u4f20\u7ed9 <code>result.toCompletionStage()</code> \u7684 <code>Future&lt;T&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>result.toCompletionStage().toCompletableFuture().get(8, TimeUnit.SECONDS)</code>
     */
    private static <T> T await(Future<T> result) {
        try {
            return result.toCompletionStage().toCompletableFuture().get(8, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected CqrsRuntimeContract createContract() {
        Vertx vertx = Vertx.vertx();
        CommandBus bus = VertxCommandBus.create(vertx, List.of(new StubExecutorA(), new StubExecutorB()));
        InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
        VertxProjectionScheduler scheduler = new VertxProjectionScheduler(vertx);
        VertxProjectionViewManager manager = new VertxProjectionViewManager(scheduler,
                new ProjectionRunner<>(new DefaultProjectionService(positions),
                        (stream, from, count, types) -> EventChunk.empty(from)), List.of());
        return new CqrsRuntimeContract() {
            public String runtimeName() {
                return "vertx";
            }

            public CommandBus commandBus() {
                return bus;
            }

            public ViewManager viewManager() {
                return manager;
            }

            public ViewScheduler viewScheduler() {
                return scheduler;
            }

            public ProjectionPositionRepository positionRepository() {
                return positions;
            }

            public void close() {
                manager.close();
                scheduler.close();
                await(vertx.close());
            }
        };
    }

    /** \u9a8c\u8bc1<code>basic.projectionManager()</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> \u7b49\u4e8e <code>before</code>\u3002 */
    @Test
    void basicModeHasNoProjectionAndMissingExplicitDependenciesPublishNothing() {
        Vertx vertx = Vertx.vertx();
        Object before = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        try (Ddd4jVertxRuntime basic = Ddd4jVertxRuntime.create(vertx, List.of())) {
            assertThat(basic.projectionManager()).isEmpty();
            assertThatThrownBy(() -> Ddd4jVertxRuntime.createWithProjection(vertx, List.of(), null,
                    (stream, from, count, types) -> EventChunk.empty(from), List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Ddd4jVertxRuntime.createWithProjection(vertx, List.of(), new TestPositions(), null, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Ddd4jVertxRuntime.createWithProjection(vertx, List.of(),
                    new InMemoryProjectionPositionRepository(), new NoopEventChunkReader<>(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)).isEqualTo(before);
        } finally {
            await(vertx.close());
        }
    }

    /**
     * \u9a8c\u8bc1<code>runtime.projectionManager().orElseThrow().isRunning()</code>\u4e3a\u5047\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>borrowedStillWorks.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void explicitFactoryBorrowsSchedulerAndRetainsStrictStorageValidation() throws Exception {
        Vertx vertx = Vertx.vertx();
        VertxProjectionScheduler scheduler = new VertxProjectionScheduler(vertx);
        AtomicInteger calls = new AtomicInteger();
        EventChunkReader<Object> reader = (stream, from, count, types) -> {
            calls.incrementAndGet();
            return EventChunk.empty(from);
        };
        Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.createWithProjection(vertx, List.of(),
                new TestPositions(), reader, List.of(view("borrowed-factory", "* * * * * *", () -> {
                })), scheduler);
        try {
            assertThat(runtime.projectionManager().orElseThrow().isRunning()).isFalse();
            assertThatThrownBy(() -> Ddd4jVertxRuntime.createWithProjection(vertx, List.of(),
                    new InMemoryProjectionPositionRepository(), reader, List.of(), scheduler))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Ddd4jVertxRuntime.createWithProjection(vertx, List.of(),
                    new TestPositions(), new NoopEventChunkReader<>(), List.of(), scheduler))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Ddd4jVertxRuntime.createWithProjection(vertx, List.of(),
                    new TestPositions(), reader, List.of(), null)).isInstanceOf(NullPointerException.class);
            runtime.start();
            await(runtime.triggerProjections());
            assertThat(calls.get()).isGreaterThanOrEqualTo(1);
            runtime.close();
            assertThatThrownBy(() -> await(runtime.triggerProjections())).isInstanceOf(RuntimeException.class);
            CountDownLatch borrowedStillWorks = new CountDownLatch(1);
            ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("after-runtime-close", "* * * * * *", borrowedStillWorks::countDown);
            assertThat(borrowedStillWorks.await(3, TimeUnit.SECONDS)).isTrue();
            handle.cancel();
            assertThat(await(vertx.executeBlocking(() -> 42))).isEqualTo(42);
        } finally {
            runtime.close();
            scheduler.close();
            await(vertx.close());
        }
    }

    /**
     * \u9a8c\u8bc1<code>Context.isOnWorkerThread()</code>\u4e3a\u771f\uff0c<code>(Object) ThreadContext.get("request")</code> \u7b49\u4e8e <code>"r11"</code>\uff0c<code>heartbeat.get(2, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void workerPropagatesRestoresAndReportsFailureWithoutBlockingActualEventLoop() throws Exception {
        Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1));
        Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.create(vertx, List.of());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<Void> finished = new CompletableFuture<>();
        try {
            await(vertx.executeBlocking(() -> {
                ThreadContext.put("worker", "previous");
                return null;
            }));
            vertx.runOnContext(ignored -> {
                try (ThreadContext.Scope scope = ThreadContext.open(Map.of("request", "r11"))) {
                    runtime.executeBlocking(() -> {
                        assertThat(Context.isOnWorkerThread()).isTrue();
                        assertThat((Object) ThreadContext.get("request")).isEqualTo("r11");
                        assertThat((Object) ThreadContext.get("worker")).isNull();
                        entered.countDown();
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                        ThreadContext.put("leak", "forbidden");
                        throw new IllegalArgumentException("worker-error");
                    }).onComplete(result -> {
                        if (result.failed() && "worker-error".equals(result.cause().getMessage())) {
                            finished.complete(null);
                        } else {
                            finished.completeExceptionally(new AssertionError("error lost"));
                        }
                    });
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Boolean> heartbeat = new CompletableFuture<>();
            vertx.setTimer(10, ignored -> heartbeat.complete(Context.isOnEventLoopThread()));
            assertThat(heartbeat.get(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            finished.get(5, TimeUnit.SECONDS);
            assertThat(await(vertx.executeBlocking(ThreadContext::getResources))).containsExactlyEntriesOf(Map.of("worker", "previous"));
            runtime.close();
            assertThat(await(vertx.executeBlocking(() -> 42))).isEqualTo(42);
        } finally {
            release.countDown();
            runtime.close();
            await(vertx.close());
        }
    }

    /**
     * \u9a8c\u8bc1<code>entered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>handle.isActive()</code>\u4e3a\u5047\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void cancellingQueuedWorkPreventsExecutionAndCloseKeepsBorrowedVertx() throws Exception {
        Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        VertxProjectionScheduler scheduler = new VertxProjectionScheduler(vertx);
        AtomicInteger calls = new AtomicInteger();
        try {
            Future<Void> blocker = vertx.executeBlocking(() -> {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("queued", "* * * * * *", calls::incrementAndGet);
            CompletableFuture<Void> elapsed = new CompletableFuture<>();
            vertx.setTimer(1200, ignored -> elapsed.complete(null));
            elapsed.get(3, TimeUnit.SECONDS);
            handle.cancel();
            scheduler.close();
            assertThat(handle.isActive()).isFalse();
            release.countDown();
            await(blocker);
            await(vertx.executeBlocking(() -> null));
            assertThat(calls).hasValue(0);
            assertThatThrownBy(() -> scheduler.schedule("closed", "* * * * * *", () -> {
            }))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            release.countDown();
            scheduler.close();
            await(vertx.close());
        }
    }

    /**
     * \u9a8c\u8bc1<code>release.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>entered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>handle.isActive()</code>\u4e3a\u5047\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void cancellationDoesNotPretendToRollbackAlreadyExecutingWork() throws Exception {
        Vertx vertx = Vertx.vertx();
        VertxProjectionScheduler scheduler = new VertxProjectionScheduler(vertx);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), committed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try {
            ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("inflight", "* * * * * *", () -> {
                entered.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    throw new IllegalStateException(failure);
                }
                calls.incrementAndGet();
                committed.countDown();
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            handle.cancel();
            scheduler.close();
            release.countDown();
            assertThat(committed.await(3, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> elapsed = new CompletableFuture<>();
            vertx.setTimer(1200, ignored -> elapsed.complete(null));
            elapsed.get(3, TimeUnit.SECONDS);
            assertThat(calls).hasValue(1);
            assertThat(handle.isActive()).isFalse();
        } finally {
            release.countDown();
            scheduler.close();
            await(vertx.close());
        }
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>runtime.projectionManager().orElseThrow().isRunning()</code>\u4e3a\u5047\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> \u7b49\u4e8e <code>before</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void failedStartAfterOwnedTimerRollsBackWithoutPublishingSpi() throws Exception {
        Vertx vertx = Vertx.vertx();
        AtomicInteger calls = new AtomicInteger();
        TestPositions positions = new TestPositions();
        EventChunkReader<Object> reader = (stream, from, count, types) -> {
            calls.incrementAndGet();
            return EventChunk.empty(from);
        };
        Object before = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.createWithProjection(vertx, List.of(), positions, reader,
                List.of(view("first", "* * * * * *", () -> {
                }), view("second", "bad", () -> {
                })));
        try {
            assertThatThrownBy(runtime::start).isInstanceOf(IllegalArgumentException.class);
            assertThat(runtime.projectionManager().orElseThrow().isRunning()).isFalse();
            CompletableFuture<Void> elapsed = new CompletableFuture<>();
            vertx.setTimer(1200, ignored -> elapsed.complete(null));
            elapsed.get(3, TimeUnit.SECONDS);
            assertThat(calls).hasValue(0);
            assertThat(BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)).isEqualTo(before);
            assertThat(await(vertx.executeBlocking(() -> 7))).isEqualTo(7);
        } finally {
            runtime.close();
            await(vertx.close());
        }
    }

    /** \u9a8c\u8bc1<code>active</code>\u4e3a\u5047\uff0c<code>startupFailure.getSuppressed()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>cancellationFailure</code>\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> \u7b49\u4e8e <code>previous</code>\u3002 */
    @Test
    void linkageFailureClosesRuntimeAndRetriesFailedBorrowedHandle() {
        Vertx vertx = Vertx.vertx();
        LinkageError startupFailure = new LinkageError("projection linkage failure");
        IllegalStateException cancellationFailure = new IllegalStateException("first cancellation failed");
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger cancellations = new AtomicInteger();
        ViewScheduler borrowed = (viewName, cron, task) -> {
            if ("second".equals(viewName)) {
                throw startupFailure;
            }
            return new ViewScheduler.ViewScheduleHandle() {
                public void cancel() {
                    if (cancellations.incrementAndGet() == 1) {
                        throw cancellationFailure;
                    }
                    active.set(false);
                }

                public boolean isActive() {
                    return active.get();
                }
            };
        };
        Object previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.createWithProjection(vertx, Collections.emptyList(),
                new TestPositions(), (stream, from, size, types) -> EventChunk.empty(from),
                Arrays.asList(view("first", "* * * * * *", () -> {
                        }),
                        view("second", "* * * * * *", () -> {
                        })), borrowed);
        try {
            assertThatThrownBy(runtime::start).isSameAs(startupFailure);
            assertThat(cancellations).hasValue(2);
            assertThat(active).isFalse();
            assertThat(startupFailure.getSuppressed()).containsExactly(cancellationFailure);
            assertThat(BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)).isEqualTo(previous);
            assertThat(await(vertx.executeBlocking(() -> 7))).isEqualTo(7);
        } finally {
            runtime.close();
            await(vertx.close());
        }
    }

    /**
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>fired.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void manualManagerRestartsAndDoesNotOwnBorrowedScheduler() throws Exception {
        Vertx vertx = Vertx.vertx();
        VertxProjectionScheduler scheduler = new VertxProjectionScheduler(vertx);
        AtomicInteger calls = new AtomicInteger();
        ProjectionRunner<Object> runner = new ProjectionRunner<>(new DefaultProjectionService(new TestPositions()),
                (stream, from, count, types) -> {
                    calls.incrementAndGet();
                    return EventChunk.empty(from);
                });
        VertxProjectionViewManager manager = new VertxProjectionViewManager(scheduler, runner,
                List.of(view("manual", "* * * * * *", () -> {
                })));
        try {
            assertThat(manager.isRunning()).isFalse();
            manager.triggerOnce();
            assertThat(calls).hasValue(1);
            manager.start();
            manager.start();
            manager.stop();
            manager.start();
            manager.stop();
            manager.close();
            assertThatThrownBy(manager::triggerOnce).isInstanceOf(IllegalStateException.class);
            CountDownLatch fired = new CountDownLatch(1);
            ViewScheduler.ViewScheduleHandle borrowed = scheduler.schedule("still-usable", "* * * * * *", fired::countDown);
            assertThat(fired.await(3, TimeUnit.SECONDS)).isTrue();
            borrowed.cancel();
        } finally {
            manager.close();
            scheduler.close();
            await(vertx.close());
        }
    }

    /** \u9a8c\u8bc1<code>positions.findByStreamId("history").orElseThrow().getNextEventNumber()</code> \u7b49\u4e8e <code>10</code>\uff0c<code>reads</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>10L</code>\u3001<code>10L</code>\u3001<code>20L</code>\uff0c<code>positions.findByStreamId("history").orElseThrow().getNextEventNumber()</code> \u7b49\u4e8e <code>20</code>\u3002 */
    @Test
    void failureDoesNotAdvanceAndRetryUsesSourceCursor() {
        Vertx vertx = Vertx.vertx();
        TestPositions positions = new TestPositions();
        positions.save(new DefaultProjectionPosition("history", 10));
        AtomicBoolean fail = new AtomicBoolean(true);
        List<Long> reads = new java.util.concurrent.CopyOnWriteArrayList<>();
        EventChunkReader<Object> reader = (stream, from, count, types) -> {
            reads.add(from);
            return from < 20 ? new EventChunk<>(List.of(11L, 19L), 20) : EventChunk.empty(from);
        };
        Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.createWithProjection(vertx, List.of(), positions, reader,
                List.of(view("history", "0 0 0 1 1 *", () -> {
                    if (fail.getAndSet(false)) {
                        throw new IllegalArgumentException("projection-failure");
                    }
                })));
        try {
            runtime.start();
            assertThatThrownBy(() -> await(runtime.triggerProjections())).hasRootCauseMessage("projection-failure");
            assertThat(positions.findByStreamId("history").orElseThrow().getNextEventNumber()).isEqualTo(10);
            await(runtime.triggerProjections());
            await(runtime.triggerProjections());
            assertThat(reads).containsExactly(10L, 10L, 20L);
            assertThat(positions.findByStreamId("history").orElseThrow().getNextEventNumber()).isEqualTo(20);
        } finally {
            runtime.close();
            await(vertx.close());
        }
    }

    /** \u9a8c\u8bc1<code>VertxProjectionScheduler.CronExpression.parse("*&#47;5 * * * * *").next(from)</code> \u7b49\u4e8e <code>from.plusSeconds(5)</code>\uff0c<code>VertxProjectionScheduler.CronExpression.parse("5/120,59/2147483647 * * * * *").next(from)</code> \u7b49\u4e8e <code>from.plusSeconds(5)</code>\uff0c<code>VertxProjectionScheduler.CronExpression.parse("*&#47;0 * * * * *")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void cronPreservesNumericDialectLocalClockAndMillionSecondWindow() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 18, 12, 0);
        assertThat(VertxProjectionScheduler.CronExpression.parse("*/5 * * * * *").next(from))
                .isEqualTo(from.plusSeconds(5));
        for (String field : new String[]{"*/60", "*/120", "0/120", "*/2147483647"}) {
            assertThat(VertxProjectionScheduler.CronExpression.parse(field + " * * * * *").next(from))
                    .as(field).isEqualTo(from.plusMinutes(1));
        }
        assertThat(VertxProjectionScheduler.CronExpression.parse("5/120,59/2147483647 * * * * *").next(from))
                .isEqualTo(from.plusSeconds(5));
        assertThat(VertxProjectionScheduler.CronExpression.parse("5/120,59/2147483647 * * * * *").next(from.plusSeconds(5)))
                .isEqualTo(from.plusSeconds(59));
        assertThat(VertxProjectionScheduler.CronExpression.parse("58/2147483646 * * * * *").next(from))
                .isEqualTo(from.plusSeconds(58));
        for (String field : new String[]{"*/-120", "60/120", "-1/120", "*/2147483648"}) {
            assertThatThrownBy(() -> VertxProjectionScheduler.CronExpression.parse(field + " * * * * *"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(VertxProjectionScheduler.CronExpression.parse("0 0 0 1 1 *").next(from)).isNull();
        assertThatThrownBy(() -> VertxProjectionScheduler.CronExpression.parse("0 0 0 ? * *"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VertxProjectionScheduler.CronExpression.parse("1-5/2 * * * * *"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VertxProjectionScheduler.CronExpression.parse("*/0 * * * * *"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 仅测试显式装配的存储替身；真实历史 H2 持久化在独立 native 进程验证。
     */
    private static final class TestPositions implements ProjectionPositionRepository {
        /** \u4f9b\u6d4b\u8bd5\u66ff\u8eab\u8f6c\u53d1\u5b9e\u9645\u884c\u4e3a\u7684\u5e95\u5c42\u5b9e\u73b0\u3002 */
        private final InMemoryProjectionPositionRepository delegate = new InMemoryProjectionPositionRepository();

        /**
         * \u901a\u8fc7 <code>delegate.findByStreamId()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param stream \u4f20\u7ed9 <code>delegate.findByStreamId()</code> \u7684 <code>String</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.findByStreamId(stream)</code>
         */
        public Optional<ProjectionPosition> findByStreamId(String stream) {
            return delegate.findByStreamId(stream);
        }

        /**
         * \u901a\u8fc7 <code>delegate.findAll()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.findAll()</code>
         */
        public List<ProjectionPosition> findAll() {
            return delegate.findAll();
        }

        /**
         * \u901a\u8fc7 <code>delegate.save()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param position \u6d4b\u8bd5\u7528\u7684\u4e8b\u4ef6\u8bfb\u53d6\u4f4d\u70b9
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.save(position)</code>
         */
        public ProjectionPosition save(ProjectionPosition position) {
            return delegate.save(position);
        }

        /**
         * \u8c03\u7528 <code>delegate.deleteByStreamId()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param stream \u4f20\u7ed9 <code>delegate.deleteByStreamId()</code> \u7684 <code>String</code> \u8f93\u5165
         */
        public void deleteByStreamId(String stream) {
            delegate.deleteByStreamId(stream);
        }

        /**
         * \u8c03\u7528 <code>delegate.resetToZero()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param stream \u4f20\u7ed9 <code>delegate.resetToZero()</code> \u7684 <code>String</code> \u8f93\u5165
         */
        public void resetToZero(String stream) {
            delegate.resetToZero(stream);
        }
    }
}
