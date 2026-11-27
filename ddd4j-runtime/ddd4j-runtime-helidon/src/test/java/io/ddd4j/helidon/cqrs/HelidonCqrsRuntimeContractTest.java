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
     * 验证<code>manager.isRunning()</code>为假，<code>tick.await(3, TimeUnit.SECONDS)</code>为真，<code>reads.get()</code> 等于 <code>stopped</code>。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 验证<code>handle.isActive()</code>为真，<code>handle.isActive()</code>为假。
     */
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
     * 验证<code>((java.util.Set&lt;?&gt;) field.get(scheduler)).isEmpty()</code>为真。
     *
     * @throws Exception 反射访问被测成员时失败
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

    /**
     * 验证<code>HelidonProjectionScheduler.CronExpression.parse("0 30 2 * * *").next(springGap)</code> 等于 <code>LocalDateTime.of(2026, 3, 29, 2, 30)</code>，<code>HelidonProjectionScheduler.CronExpression.parse("0 0 0 1 1 *") .next(LocalDateTime.of(2026, 2, 1, 0, 0))</code>为空，<code>HelidonProjectionScheduler.CronExpression.parse("*&#47;0 * * * * *")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
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
     * 构造 <code>ProjectionView&lt;&gt;</code> 作为当前测试的输入或结果。
     *
     * @param name 当前测试指定的对象或属性名称
     * @param cron 用于构造本次返回结果的 <code>String</code> 输入
     * @return 提供给后续契约断言的 <code>ProjectionView&lt;Object&gt;</code> 结果
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
