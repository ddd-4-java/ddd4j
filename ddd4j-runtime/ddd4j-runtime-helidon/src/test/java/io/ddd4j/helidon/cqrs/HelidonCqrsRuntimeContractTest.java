package io.ddd4j.helidon.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
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

/** 使用真实 Helidon SE 适配器，原生 MP/JAR/持久恢复另有集成证据。 */
class HelidonCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {
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
            public String runtimeName() { return "helidon"; }
            public CommandBus commandBus() { return bus; }
            public ViewManager viewManager() { return manager; }
            public ViewScheduler viewScheduler() { return scheduler; }
            public ProjectionPositionRepository positionRepository() { return positions; }
            public void close() { manager.stop(); scheduler.shutdown(); }
        };
    }

    @Test
    void realScheduledTaskRunsOncePerRegistrationAndCancelsOnStop() throws Exception {
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch tick = new CountDownLatch(1);
        ProjectionRunner<Object> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()), (s,p,n,t) -> {
                    reads.incrementAndGet(); tick.countDown(); return EventChunk.empty(p);
                });
        HelidonProjectionViewManager manager = new HelidonProjectionViewManager(scheduler, runner,
                List.of(view("one", "* * * * * *")));
        try {
            assertFalse(manager.isRunning());
            manager.start(); manager.start();
            assertTrue(tick.await(3, TimeUnit.SECONDS));
            manager.stop();
            int stopped = reads.get();
            Thread.sleep(1200);
            assertEquals(stopped, reads.get());
            assertFalse(manager.isRunning());
        } finally { manager.stop(); scheduler.shutdown(); }
    }

    @Test
    void shutdownMarksPendingHandlesInactive() {
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("close", "0 * * * * *", () -> { });
        assertTrue(handle.isActive());
        scheduler.shutdown();
        assertFalse(handle.isActive());
    }

    @Test
    void cancellationReleasesSchedulerTaskRegistration() throws Exception {
        HelidonProjectionScheduler scheduler = new HelidonProjectionScheduler();
        try {
            ViewScheduler.ViewScheduleHandle handle = scheduler.schedule("cancel-release", "0 * * * * *", () -> { });
            handle.cancel();
            java.lang.reflect.Field field = HelidonProjectionScheduler.class.getDeclaredField("handles");
            field.setAccessible(true);
            assertTrue(((java.util.Set<?>) field.get(scheduler)).isEmpty(), "cancel must release captured task registration");
        } finally { scheduler.shutdown(); }
    }

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

    private ProjectionView<Object> view(String name, String cron) {
        return new ProjectionView<>() {
            public String getName() { return name; }
            public String getCron() { return cron; }
            public Collection<String> getEventTypes() { return List.of("event"); }
            public void handleEvents(Collection<Object> events) { }
        };
    }
}
