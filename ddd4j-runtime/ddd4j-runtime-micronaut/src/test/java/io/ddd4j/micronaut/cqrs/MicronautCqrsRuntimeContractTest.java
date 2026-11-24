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
package io.ddd4j.micronaut.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.NoopEventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.micronaut.command.MicronautCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Micronaut 真实原生容器与共享命令、视图契约；持久恢复另以当前 JAR 探针验证。 */
class MicronautCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {
    private static final String PARTIAL_START = "r14.micronaut.partial-start";

    @Test
    void nativeBasicContextUsesSnapshotBusAndNoProjection() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertTrue(context.containsBean(CommandRegistry.class));
            assertEquals(MicronautCommandBus.class, context.getBean(CommandBus.class).getClass());
            assertFalse(context.containsBean(ViewManager.class));
            assertFalse(context.containsBean(ProjectionPositionRepository.class));
            assertFalse(context.containsBean(EventChunkReader.class));
            assertFalse(context.containsBean(ViewScheduler.class));
        }
    }

    @Test
    void nativeContextCloseRestoresPriorSpi() {
        CommandBus prior = new DefaultCommandBus(List.of());
        BaseContext.inject(SpiKeys.COMMAND_BUS, CommandBus.class, prior);
        try {
            try (ApplicationContext context = ApplicationContext.run()) {
                assertEquals(context.getBean(CommandBus.class), BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow());
            }
            assertEquals(prior, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow());
        } finally {
            BaseContext.remove(SpiKeys.COMMAND_BUS);
        }
    }

    @Test
    void generatedHandlerDiscoveryPreservesSnapshotAndFailureIdentity() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("r09.command", true))) {
            CommandBus bus = context.getBean(CommandBus.class);
            assertEquals(1, context.getBeansOfType(CommandBus.class).size());
            assertEquals("native", bus.execute(new NativeCommand()).data().orElseThrow());
            CommandRegistry registry = context.getBean(CommandRegistry.class);
            registry.register(new CommandExecutor<LateCommand>() {
                @Override
                public Set<Class<? extends Command>> supportedCommands() {
                    return Set.of(LateCommand.class);
                }
                @Override
                public Result execute(LateCommand command) {
                    return Result.ok();
                }
            });
            assertThrows(IllegalStateException.class, () -> bus.execute(new LateCommand()));
            assertThrows(NullPointerException.class, () -> new MicronautCommandBus(null, registry));
            assertThrows(NullPointerException.class, () -> new MicronautCommandBus(context, null));
            NativeExecutor executor = context.getBean(NativeExecutor.class);
            executor.fail = true;
            assertEquals(executor.failure, assertThrows(IllegalArgumentException.class,
                    () -> bus.execute(new NativeCommand())));
        }
    }

    @Test
    void generatedHandlerDiscoveryRejectsOverlappingCommands() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("r09.command", true))) {
            CommandRegistry registry = new CommandRegistry();
            registry.register(new CommandExecutor<Command>() {
                @Override
                public Set<Class<? extends Command>> supportedCommands() {
                    return Set.of(NativeCommand.class, LateCommand.class);
                }

                @Override
                public Result execute(Command command) {
                    return Result.ok();
                }
            });

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new MicronautCommandBus(context, registry));

            assertTrue(failure.getMessage().contains(NativeCommand.class.getName()));
        }
    }

    @Test
    void explicitProjectionWithoutDependenciesFailsAtNativeStartup() {
        ApplicationContext context = ApplicationContext.builder()
                .properties(Map.of(MicronautProjectionViewManager.ENABLED, true)).build();
        try {
            assertThrows(RuntimeException.class, context::start);
        } finally {
            context.close();
        }
    }

    @Test
    void explicitProjectionRejectsMemoryAndNoopBeforeScheduling() {
        ApplicationContext context = ApplicationContext.builder()
                .properties(Map.of(MicronautProjectionViewManager.ENABLED, true)).build();
        context.registerSingleton(ProjectionPositionRepository.class, new InMemoryProjectionPositionRepository());
        context.registerSingleton(EventChunkReader.class, new NoopEventChunkReader<>());
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, context::start);
            assertTrue(failure.getMessage().contains("durable positions"));
        } finally {
            context.close();
        }
    }

    @Test
    void nativePartialStartupRetriesCancellationAndPreservesPriorSpi() {
        CommandBus prior = new DefaultCommandBus(Collections.emptyList());
        Optional<CommandBus> previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        BaseContext.inject(SpiKeys.COMMAND_BUS, CommandBus.class, prior);

        AtomicInteger schedules = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicBoolean active = new AtomicBoolean();
        LinkageError startup = new LinkageError("second projection schedule failed");
        IllegalStateException firstCancel = new IllegalStateException("first cancel failed");
        Map<String, Object> properties = new HashMap<>();
        properties.put(MicronautProjectionViewManager.ENABLED, true);
        properties.put(PARTIAL_START, true);
        ApplicationContext context = ApplicationContext.builder().properties(properties).build();
        try {
            context.registerSingleton(ProjectionPositionRepository.class, new UnusedRepository());
            context.registerSingleton(ProjectionView.class, unscheduledView("one"), Qualifiers.byName("one"));
            context.registerSingleton(ProjectionView.class, unscheduledView("two"), Qualifiers.byName("two"));
            context.registerSingleton(ViewScheduler.class, (name, cron, task) -> {
                if (schedules.incrementAndGet() == 2) {
                    throw startup;
                }
                active.set(true);
                return new ViewScheduler.ViewScheduleHandle() {
                    @Override
                    public void cancel() {
                        if (cancellations.incrementAndGet() == 1) {
                            throw firstCancel;
                        }
                        active.set(false);
                    }

                    @Override
                    public boolean isActive() {
                        return active.get();
                    }
                };
            });

            Throwable reported = assertThrows(Throwable.class, context::start);
            Throwable failure = reported;
            while (Objects.nonNull(failure) && failure != startup) {
                failure = failure.getCause();
            }

            assertSame(startup, failure, () -> "Micronaut startup reported " + reported
                    + "; schedules=" + schedules.get() + "; cancellations=" + cancellations.get());
            assertEquals(2, schedules.get());
            assertEquals(2, cancellations.get());
            assertFalse(active.get());
            assertEquals(1, startup.getSuppressed().length);
            assertSame(firstCancel, startup.getSuppressed()[0]);
            assertSame(prior, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow(AssertionError::new));
        } finally {
            try {
                context.close();
            } finally {
                if (previous.isPresent()) {
                    BaseContext.inject(SpiKeys.COMMAND_BUS, CommandBus.class, previous.get());
                } else {
                    BaseContext.remove(SpiKeys.COMMAND_BUS);
                }
            }
        }
    }

    private static ProjectionView<Object> unscheduledView(String name) {
        return new ProjectionView<Object>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCron() {
                return "0 0 0 * * *";
            }

            @Override
            public Collection<String> getEventTypes() {
                return Collections.singleton("event");
            }

            @Override
            public void handleEvents(Collection<Object> events) {
                throw new AssertionError("failed startup must not handle events");
            }
        };
    }

    @Singleton
    @Requires(property = PARTIAL_START, value = "true")
    static class UnusedReader implements EventChunkReader<Object> {
        @Override
        public EventChunk<Object> read(String stream, long from, int size, Collection<String> types) {
            throw new AssertionError("failed startup must not read events");
        }
    }

    private static final class UnusedRepository implements ProjectionPositionRepository {
        @Override
        public Optional<ProjectionPosition> findByStreamId(String streamId) {
            throw unexpectedAccess();
        }

        @Override
        public List<ProjectionPosition> findAll() {
            throw unexpectedAccess();
        }

        @Override
        public ProjectionPosition save(ProjectionPosition position) {
            throw unexpectedAccess();
        }

        @Override
        public void deleteByStreamId(String streamId) {
            throw unexpectedAccess();
        }

        @Override
        public void resetToZero(String streamId) {
            throw unexpectedAccess();
        }

        private AssertionError unexpectedAccess() {
            return new AssertionError("failed startup must not use projection positions");
        }
    }

    @Test
    void sharedCronRetainsNumericGrammarAndFindsAnnualSchedule() {
        LocalDateTime from = LocalDateTime.of(2026, 2, 1, 0, 0);
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0),
                MicronautProjectionScheduler.CronExpression.parse("0 0 0 1 1 *").next(from));
        assertThrows(IllegalArgumentException.class,
                () -> MicronautProjectionScheduler.CronExpression.parse("0 0 0 ? * *"));
        assertThrows(IllegalArgumentException.class,
                () -> MicronautProjectionScheduler.CronExpression.parse("0/0 * * * * *"));
    }

    @Test
    void cronRetainsLegacyLocalWallTimeAtDstGap() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertEquals(LocalDateTime.of(2026, 3, 8, 2, 0),
                    MicronautProjectionScheduler.CronExpression.parse("0 0 2 * * *")
                            .next(LocalDateTime.of(2026, 3, 8, 1, 59, 59)));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Override
    protected CqrsRuntimeContract createContract() {
        ApplicationContext context = ApplicationContext.builder().build();
        context.registerSingleton(StubExecutorA.class, new StubExecutorA());
        context.registerSingleton(StubExecutorB.class, new StubExecutorB());
        context.start();
        CommandBus commandBus = context.getBean(CommandBus.class);
        InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
        MicronautProjectionScheduler scheduler = new MicronautProjectionScheduler();
        MicronautProjectionViewManager manager = new MicronautProjectionViewManager(scheduler,
                new ProjectionRunner<>(new DefaultProjectionService(positions), new NoopEventChunkReader<>()), context);
        return new CqrsRuntimeContract() {
            @Override
            public String runtimeName() { return "micronaut"; }
            @Override
            public CommandBus commandBus() { return commandBus; }
            @Override
            public ViewManager viewManager() { return manager; }
            @Override
            public ViewScheduler viewScheduler() { return scheduler; }
            @Override
            public ProjectionPositionRepository positionRepository() { return positions; }
            @Override
            public void close() {
                manager.stop();
                scheduler.shutdown();
                context.close();
            }
        };
    }

    /** 原生编译期发现使用的命令。 */
    static final class NativeCommand implements Command { }
    /** 构造总线后才注册的命令。 */
    static final class LateCommand implements Command { }
    /** 原生 BeanDefinition 发现的执行器；保留异常对象身份。 */
    @Singleton
    @Requires(property = "r09.command", value = "true")
    static class NativeExecutor implements CommandExecutor<NativeCommand> {
        boolean fail;
        final IllegalArgumentException failure = new IllegalArgumentException("handler failure");
        @Override
        public Set<Class<? extends Command>> supportedCommands() { return Set.of(NativeCommand.class); }
        @Override
        public Result execute(NativeCommand command) {
            if (fail) {
                throw failure;
            }
            return Result.ok("native");
        }
    }
}
