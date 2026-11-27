package io.ddd4j.helidon.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.helidon.command.HelidonCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实 Helidon SE 适配器，原生 MP/JAR/持久恢复另有集成证据。
 */
class HelidonCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {
    /**
     * 创建测试中用于操作当前框架的契约适配器。
     *
     * <p>测试夹具会调用 <code>ServiceLoader.load(CommandExecutor.class)</code>、<code>HelidonServiceLoader.builder(services).useSystemServiceLoader(false).addService(new StubExecutorA())</code>。</p>
     *
     * @return CQRS 运行时契约
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected CqrsRuntimeContract createContract() {
        ServiceLoader<CommandExecutor<?>> services = (ServiceLoader) ServiceLoader.load(CommandExecutor.class);
        HelidonCommandBus bus = new HelidonCommandBus(HelidonServiceLoader.builder(services)
                .useSystemServiceLoader(false).addService(new StubExecutorA()).addService(new StubExecutorB())
                .build(), new CommandRegistry());
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
        HelidonProjectionViewManager manager = new HelidonProjectionViewManager(scheduler,
                new ProjectionRunner<>(new DefaultProjectionService(positions),
                        (s, p, n, t) -> EventChunk.empty(p)), List.of());
        return new CqrsRuntimeContract() {
            public String runtimeName() {
                return "helidon";
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
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c<code>tick.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>reads.get()</code> \u7b49\u4e8e <code>stopped</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void realScheduledTaskRunsOncePerRegistrationAndCancelsOnStop() throws Exception {
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch tick = new CountDownLatch(1);
        ProjectionRunner<Object> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()), (s, p, n, t) -> {
            reads.incrementAndGet();
            tick.countDown();
            return EventChunk.empty(p);
        });
        HelidonProjectionViewManager manager = new HelidonProjectionViewManager(scheduler, runner,
                List.of(view("one", "* * * * * *")));
        try {
            assertFalse(manager.isRunning());
            manager.start();
            manager.start();
            assertTrue(tick.await(3, TimeUnit.SECONDS));
            manager.stop();
            int stopped = reads.get();
            Thread.sleep(1200);
            assertEquals(stopped, reads.get());
            assertFalse(manager.isRunning());
        } finally {
            manager.stop();
            scheduler.shutdown();
        }
    }

    /** \u9a8c\u8bc1<code>handle.isActive()</code>\u4e3a\u771f\uff0c<code>handle.isActive()</code>\u4e3a\u5047\u3002 */
    @Test
    void shutdownMarksPendingHandlesInactive() {
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("close", "0 * * * * *", () -> {
        });
        assertTrue(handle.isActive());
        scheduler.shutdown();
        assertFalse(handle.isActive());
    }

    /**
     * \u9a8c\u8bc1<code>((java.util.Set&lt;?&gt;) field.get(scheduler)).isEmpty()</code>\u4e3a\u771f\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void cancellationReleasesSchedulerTaskRegistration() throws Exception {
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        try {
            ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("cancel-release", "0 * * * * *", () -> {
            });
            handle.cancel();
            java.lang.reflect.Field field = HelidonProjectionScheduler.class.getDeclaredField("handles");
            field.setAccessible(true);
            assertTrue(((java.util.Set<?>) field.get(scheduler)).isEmpty(), "cancel must release captured task registration");
        } finally {
            scheduler.shutdown();
        }
    }

    /** \u9a8c\u8bc1<code>HelidonProjectionScheduler.CronExpression.parse("0 30 2 * * *").next(springGap)</code> \u7b49\u4e8e <code>LocalDateTime.of(2026, 3, 29, 2, 30)</code>\uff0c<code>HelidonProjectionScheduler.CronExpression.parse("0 0 0 1 1 *") .next(LocalDateTime.of(2026, 2, 1, 0, 0))</code>\u4e3a\u7a7a\uff0c<code>HelidonProjectionScheduler.CronExpression.parse("*&#47;0 * * * * *")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void legacyCronKeepsWallClockAndSearchWindowAndRejectsNewDialect() {
        LocalDateTime springGap = LocalDateTime.of(2026, 3, 29, 1, 59, 59);
        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30),
                HelidonProjectionScheduler.CronExpression.parse("0 30 2 * * *").next(springGap));
        assertNull(HelidonProjectionScheduler.CronExpression.parse("0 0 0 1 1 *")
                .next(LocalDateTime.of(2026, 2, 1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> HelidonProjectionScheduler.CronExpression.parse("0 0 0 ? * *"));
        assertThrows(IllegalArgumentException.class, () -> HelidonProjectionScheduler.CronExpression.parse("1-5/2 * * * * *"));
        assertThrows(IllegalArgumentException.class, () -> HelidonProjectionScheduler.CronExpression.parse("*/0 * * * * *"));
    }

    /**
     * \u6784\u9020 <code>ProjectionView&lt;&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param cron \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;Object&gt;</code> \u7ed3\u679c
     */
    private ProjectionView<Object> view(String name, String cron) {
        return new ProjectionView<>() {
            public String getName() {
                return name;
            }

            public String getCron() {
                return cron;
            }

            public Collection<String> getEventTypes() {
                return List.of("event");
            }

            public void handleEvents(Collection<Object> events) {
            }
        };
    }
}
