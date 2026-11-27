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

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.command.*;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.micronaut.command.MicronautCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Micronaut 真实原生容器与共享命令、视图契约；持久恢复另以当前 JAR 探针验证。
 */
class MicronautCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {
    /** \u7528\u6765\u590d\u73b0\u542f\u52a8\u4e2d\u9014\u5931\u8d25\u6216\u672a\u5b8c\u6210\u7684\u6d4b\u8bd5\u914d\u7f6e\u3002 */
    private static final String PARTIAL_START = "r14.micronaut.partial-start";

    /**
     * \u6784\u9020 <code>ProjectionView&lt;Object&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;Object&gt;</code> \u7ed3\u679c
     */
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

    /** \u9a8c\u8bc1<code>context.containsBean(CommandRegistry.class)</code>\u4e3a\u771f\uff0c<code>context.getBean(CommandBus.class).getClass()</code> \u7b49\u4e8e <code>MicronautCommandBus.class</code>\uff0c<code>context.containsBean(ViewScheduler.class)</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow()</code> \u7b49\u4e8e <code>context.getBean(CommandBus.class)</code>\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow()</code> \u7b49\u4e8e <code>prior</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>context.getBeansOfType(CommandBus.class).size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>bus.execute(new NativeCommand()).data().orElseThrow()</code> \u7b49\u4e8e <code>"native"</code>\uff0c<code>bus.execute(new NativeCommand())</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage().contains(NativeCommand.class.getName())</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>failure.getMessage().contains("durable positions")</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>Throwable.class</code>\uff0c<code>failure</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>startup</code>\uff0c<code>startup.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>firstCancel</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>MicronautProjectionScheduler.CronExpression.parse("0 0 0 1 1 *").next(from)</code> \u7b49\u4e8e <code>LocalDateTime.of(2027, 1, 1, 0, 0)</code>\uff0c<code>MicronautProjectionScheduler.CronExpression.parse("0 0 0 ? * *")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>MicronautProjectionScheduler.CronExpression.parse("0/0 * * * * *")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>TimeZone.getDefault()</code>\u3001<code>TimeZone.setDefault()</code>\u3001<code>TimeZone.getTimeZone()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /**
     * 创建测试中用于操作当前框架的契约适配器。
     *
     * <p>测试夹具会调用 <code>ApplicationContext.builder().build()</code>、<code>ApplicationContext.builder()</code>。</p>
     *
     * @return CQRS 运行时契约
     */
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
            public String runtimeName() {
                return "micronaut";
            }

            @Override
            public CommandBus commandBus() {
                return commandBus;
            }

            @Override
            public ViewManager viewManager() {
                return manager;
            }

            @Override
            public ViewScheduler viewScheduler() {
                return scheduler;
            }

            @Override
            public ProjectionPositionRepository positionRepository() {
                return positions;
            }

            @Override
            public void close() {
                manager.stop();
                scheduler.shutdown();
                context.close();
            }
        };
    }

    /** \u5b9e\u73b0 <code>EventChunkReader</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>read()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    @Singleton
    @Requires(property = PARTIAL_START, value = "true")
    static class UnusedReader implements EventChunkReader<Object> {
        /**
         * 读取调用指定的数据或事件流。
         *
         * @param stream Redis Stream 的名称
         * @param from 用于此操作的 <code>from</code>（<code>long</code>）
         * @param size 用于此操作的 <code>size</code>（<code>int</code>）
         * @param types 用于此操作的 <code>types</code>（<code>Collection&lt;String&gt;</code>）
         * @return 事件块
         */
        @Override
        public EventChunk<Object> read(String stream, long from, int size, Collection<String> types) {
            throw new AssertionError("failed startup must not read events");
        }
    }

    /** \u6a21\u62df\u6295\u5f71\u4f4d\u70b9\u5b58\u50a8\u5e76\u63a7\u5236\u5199\u5165\u884c\u4e3a\u3002 */
    private static final class UnusedRepository implements ProjectionPositionRepository {
        /**
         * 查询指定事件流的投影检查点。
         *
         * <p>测试夹具会调用 <code>unexpectedAccess()</code>。</p>
         *
         * @param streamId 投影流 ID
         * @return 投影位置（不存在时返回 {@code Optional#empty()}）
         */
        @Override
        public Optional<ProjectionPosition> findByStreamId(String streamId) {
            throw unexpectedAccess();
        }

        /**
         * 查询存储中符合条件的记录。
         *
         * <p>测试夹具会调用 <code>unexpectedAccess()</code>。</p>
         *
         * @return 全部投影位置
         */
        @Override
        public List<ProjectionPosition> findAll() {
            throw unexpectedAccess();
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * <p>测试夹具会调用 <code>unexpectedAccess()</code>。</p>
         *
         * @param position 投影位置
         * @return 持久化后的投影位置
         */
        @Override
        public ProjectionPosition save(ProjectionPosition position) {
            throw unexpectedAccess();
        }

        /**
         * 删除指定事件流的投影检查点。
         *
         * <p>测试夹具会调用 <code>unexpectedAccess()</code>。</p>
         *
         * @param streamId 投影流 ID
         */
        @Override
        public void deleteByStreamId(String streamId) {
            throw unexpectedAccess();
        }

        /**
         * 将指定流的投影检查点重置为零。
         *
         * <p>测试夹具会调用 <code>unexpectedAccess()</code>。</p>
         *
         * @param streamId 投影流 ID
         */
        @Override
        public void resetToZero(String streamId) {
            throw unexpectedAccess();
        }

        /**
         * \u521b\u5efa <code>new AssertionError</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AssertionError("failed startup must not use projection positions")</code>
         */
        private AssertionError unexpectedAccess() {
            return new AssertionError("failed startup must not use projection positions");
        }
    }

    /**
     * 原生编译期发现使用的命令。
     */
    static final class NativeCommand implements Command {
    }

    /**
     * 构造总线后才注册的命令。
     */
    static final class LateCommand implements Command {
    }

    /**
     * 原生 BeanDefinition 发现的执行器；保留异常对象身份。
     */
    @Singleton
    @Requires(property = "r09.command", value = "true")
    static class NativeExecutor implements CommandExecutor<NativeCommand> {
        /** \u5f53\u524d\u5931\u8d25\u8def\u5f84\u6240\u7528\u6216\u89c2\u5bdf\u5230\u7684\u539f\u59cb\u5f02\u5e38\u3002 */
        final IllegalArgumentException failure = new IllegalArgumentException("handler failure");
        /** \u4ee4\u5f53\u524d\u66ff\u8eab\u5728\u6307\u5b9a\u6d4b\u8bd5\u8def\u5f84\u4e2d\u8fd4\u56de\u5931\u8d25\u3002 */
        boolean fail;

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Set.of(NativeCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Set.of(NativeCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(NativeCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>Result.ok("native")</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>Result.ok("native")</code>
         */
        @Override
        public Result execute(NativeCommand command) {
            if (fail) {
                throw failure;
            }
            return Result.ok("native");
        }
    }
}
