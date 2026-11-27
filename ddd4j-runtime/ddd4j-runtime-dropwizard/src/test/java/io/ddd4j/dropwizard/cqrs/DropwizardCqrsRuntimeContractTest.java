package io.ddd4j.dropwizard.cqrs;

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.dropwizard.command.DropwizardCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 runtime 命令/调度适配器的契约；内存仓储仅是隔离测试夹具。
 */
class DropwizardCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {
    /**
     * 构造 <code>ProjectionView&lt;&gt;</code> 作为当前测试的输入或结果。
     *
     * @param name    当前测试指定的对象或属性名称
     * @param cron    用于构造本次返回结果的 <code>String</code> 输入
     * @param handler 处理当前测试请求或事件的组件
     * @return 提供给后续契约断言的 <code>ProjectionView&lt;Object&gt;</code> 结果
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
     * 创建测试中用于操作当前框架的契约适配器。
     *
     * <p>测试夹具会调用 <code>List.of(new StubExecutorA(), new StubExecutorB())</code>、<code>DropwizardProjectionScheduler.create(new Environment("contract"), List.of(), runner)</code>。</p>
     *
     * @return CQRS 运行时契约
     */
    @Override
    protected CqrsRuntimeContract createContract() {
        DropwizardCommandBus bus = new DropwizardCommandBus(List.of(new StubExecutorA(), new StubExecutorB()));
        InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
        ProjectionRunner<Object> runner = new ProjectionRunner<>(new DefaultProjectionService(positions), new NoopEventChunkReader<>());
        DropwizardProjectionScheduler scheduler = DropwizardProjectionScheduler.create(new Environment("contract"), List.of(), runner);
        DropwizardProjectionViewManager manager = new DropwizardProjectionViewManager(scheduler, runner, List.of());
        return new CqrsRuntimeContract() {
            public String runtimeName() {
                return "dropwizard";
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
                manager.stop();
                scheduler.shutdown();
            }
        };
    }

    /**
     * 验证<code>bus.execute(new CmdB(1))</code> 抛出 <code>IllegalStateException.class</code>，被测调用 抛出 <code>IllegalStateException.class</code>，被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void commandSnapshotDuplicateAndHandlerFailure() {
        List<CommandExecutor<?>> candidates = new ArrayList<>(List.of(new StubExecutorA()));
        DropwizardCommandBus bus = new DropwizardCommandBus(candidates);
        candidates.add(new StubExecutorB());
        assertThrows(IllegalStateException.class, () -> bus.execute(new CmdB(1)));
        assertThrows(IllegalStateException.class, () -> new DropwizardCommandBus(List.of(new StubExecutorA(), new StubExecutorA())));
        IllegalArgumentException failure = new IllegalArgumentException("application transaction failure");
        CommandExecutor<CmdA> executor = new CommandExecutor<>() {
            public Set<Class<? extends io.ddd4j.core.cqrs.command.Command>> supportedCommands() {
                return Set.of(CmdA.class);
            }

            public R<?> execute(CmdA command) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new DropwizardCommandBus(List.of(executor)).execute(new CmdA("x"))));
    }

    /**
     * 验证<code>manager.isRunning()</code>为假，被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>manager.getProjectionStatus("orders").nextEventNumber()</code> 等于 <code>20</code>。
     */
    @Test
    void sourceCursorRetryAndOwnedRestartUseSharedRunner() {
        InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
        positions.save(new DefaultProjectionPosition("orders", 10));
        List<Long> reads = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger applied = new AtomicInteger();
        ProjectionView<Object> view = view("orders", "0 0 0 1 1 *", () -> {
            if (fail.getAndSet(false)) {
                throw new IllegalArgumentException("retry");
            }
            applied.incrementAndGet();
        });
        ProjectionRunner<Object> runner = new ProjectionRunner<>(new DefaultProjectionService(positions),
                (stream, from, count, types) -> {
                    reads.add(from);
                    return from < 20 ? new EventChunk<>(List.of(11L, 19L), 20) : EventChunk.empty(from);
                });
        DropwizardProjectionViewManager manager = DropwizardProjectionViewManager.create(new Environment("retry"), List.of(view), runner);
        try {
            assertFalse(manager.isRunning());
            manager.start();
            manager.start();
            assertThrows(IllegalArgumentException.class, manager::triggerOnce);
            assertEquals(10, positions.findByStreamId("orders").orElseThrow().getNextEventNumber());
            manager.triggerOnce();
            manager.stop();
            manager.start();
            manager.triggerOnce();
            assertEquals(List.of(10L, 10L, 20L), reads);
            assertEquals(1, applied.get());
            assertEquals(20, manager.getProjectionStatus("orders").nextEventNumber());
        } finally {
            manager.stop();
        }
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>second.get()</code> 等于 <code>0</code>，<code>DropwizardProjectionViewManager.create(new Environment("duplicate"), List.of(first, first), runner)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void sharedManagerKeepsRunAllFailFastAndRejectsDuplicateViews() {
        AtomicInteger second = new AtomicInteger();
        ProjectionRunner<Object> runner = new ProjectionRunner<>(new DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                (s, f, n, t) -> new EventChunk<>(List.of("event"), f + 1));
        ProjectionView<Object> first = view("first", "0 0 0 1 1 *", () -> {
            throw new IllegalArgumentException("first");
        });
        ProjectionView<Object> last = view("last", "0 0 0 1 1 *", second::incrementAndGet);
        DropwizardProjectionViewManager manager = DropwizardProjectionViewManager.create(new Environment("aggregate"), List.of(first, last), runner);
        assertThrows(IllegalArgumentException.class, manager::triggerOnce);
        assertEquals(0, second.get()); // 旧 triggerOnce 调用 runAll，首个异常阻止后续视图。
        assertThrows(IllegalArgumentException.class,
                () -> DropwizardProjectionViewManager.create(new Environment("duplicate"), List.of(first, first), runner));
        manager.stop();
    }

    /**
     * 验证<code>DropwizardProjectionScheduler.CronExpression.parse("*&#47;120 * * * * *").next(from)</code> 等于 <code>from.withMinute(1).withSecond(0)</code>，<code>DropwizardProjectionScheduler.CronExpression.parse("5-7,9 * * * * *").next(from)</code> 等于 <code>from.withSecond(5)</code>，<code>DropwizardProjectionScheduler.CronExpression.parse(cron)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void numericDialectAndMillionSecondWindowRemainCompatible() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0, 1);
        assertEquals(from.withMinute(1).withSecond(0), DropwizardProjectionScheduler.CronExpression.parse("*/120 * * * * *").next(from));
        assertEquals(from.withSecond(5), DropwizardProjectionScheduler.CronExpression.parse("5-7,9 * * * * *").next(from));
        assertNull(DropwizardProjectionScheduler.CronExpression.parse("0 0 0 1 2 *").next(from));
        for (String cron : List.of("* * * ? * *", "0-10/2 * * * * *", "*/0 * * * * *", "* * * * * MON")) {
            assertThrows(IllegalArgumentException.class, () -> DropwizardProjectionScheduler.CronExpression.parse(cron));
        }
    }

    /**
     * 验证<code>entered.await(3, TimeUnit.SECONDS)</code>为真，<code>interrupted.await(3, TimeUnit.SECONDS)</code>为真，<code>callback.await(3, TimeUnit.SECONDS)</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void ownedShutdownWaitsForEnteredTaskAndBorrowedSchedulerRemainsUsable() throws Exception {
        ProjectionRunner<Object> runner = new ProjectionRunner<>(new DefaultProjectionService(new InMemoryProjectionPositionRepository()), new NoopEventChunkReader<>());
        DropwizardProjectionScheduler scheduler = DropwizardProjectionScheduler.create(new Environment("shutdown"), List.of(), runner);
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        CompletableFuture<Void> shutdown = null;
        try {
            scheduler.schedule("blocked", "* * * * * *", () -> {
                entered.countDown();
                try {
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException expected) {
                            interrupted.countDown();
                        }
                    }
                } finally {
                    finished.countDown();
                }
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            shutdown = CompletableFuture.runAsync(scheduler::shutdown);
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            assertFalse(shutdown.isDone());
            release.countDown();
            shutdown.get(3, TimeUnit.SECONDS);
            assertEquals(0, finished.getCount());
            assertTrue(scheduler.isTerminated());
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
        DropwizardProjectionScheduler borrowed = DropwizardProjectionScheduler.create(new Environment("borrowed"), List.of(), runner);
        try {
            DropwizardProjectionViewManager manager = new DropwizardProjectionViewManager(borrowed, runner, List.of(view("borrowed", "* * * * * *", () -> {
            })));
            manager.start();
            manager.stop();
            CountDownLatch callback = new CountDownLatch(1);
            borrowed.schedule("still-usable", "* * * * * *", callback::countDown);
            assertTrue(callback.await(3, TimeUnit.SECONDS));
        } finally {
            borrowed.shutdown();
        }
    }

    /**
     * 验证被测调用 抛出 <code>LinkageError.class</code>，<code>created.get()</code>非空，<code>manager.isRunning()</code>为假。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void ownedSchedulerTerminatesAfterSecondViewThrowsLinkageError() throws Exception {
        LinkageError failure = new LinkageError("projection linkage failure");
        Field scheduler = DropwizardProjectionViewManager.class.getDeclaredField("ownedScheduler");
        scheduler.setAccessible(true);
        AtomicReference<DropwizardProjectionViewManager> managerRef = new AtomicReference<>();
        AtomicReference<DropwizardProjectionScheduler> created = new AtomicReference<>();
        ProjectionView<Object> invalid = new ProjectionView<Object>() {
            public String getName() {
                return "broken";
            }

            public String getCron() {
                try {
                    created.set((DropwizardProjectionScheduler) scheduler.get(managerRef.get()));
                } catch (IllegalAccessException ex) {
                    throw new AssertionError(ex);
                }
                throw failure;
            }

            public Collection<String> getEventTypes() {
                return Collections.emptyList();
            }

            public void handleEvents(Collection<Object> events) {
            }
        };
        ProjectionRunner<Object> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()), new NoopEventChunkReader<>());
        DropwizardProjectionViewManager manager = DropwizardProjectionViewManager.create(new Environment("linkage-rollback"),
                Arrays.asList(view("first", "* * * * * *", () -> {
                }), invalid), runner);
        managerRef.set(manager);
        try {
            assertSame(failure, assertThrows(LinkageError.class, manager::start));
            assertNotNull(created.get());
            assertTrue(created.get().isTerminated());
            assertFalse(manager.isRunning());
        } finally {
            manager.close();
        }
    }
}
