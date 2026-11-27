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
import io.ddd4j.core.api.R;
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
    /**
     * 用来复现启动中途失败或未完成的测试配置。
     */
    private static final String PARTIAL_START = "r14.micronaut.partial-start";

    /**
     * 构造 <code>ProjectionView&lt;Object&gt;</code> 作为当前测试的输入或结果。
     *
     * @param name 当前测试指定的对象或属性名称
     * @return 提供给后续契约断言的 <code>ProjectionView&lt;Object&gt;</code> 结果
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

    /**
     * 验证<code>context.containsBean(CommandRegistry.class)</code>为真，<code>context.getBean(CommandBus.class).getClass()</code> 等于 <code>MicronautCommandBus.class</code>，<code>context.containsBean(ViewScheduler.class)</code>为假。
     */
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

    /**
     * 验证<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow(AssertionError::new)</code> 等于 <code>context.getBean(CommandBus.class)</code>，<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow(AssertionError::new)</code> 等于 <code>prior</code>。
     */
    @Test
    void nativeContextCloseRestoresPriorSpi() {
        CommandBus prior = new DefaultCommandBus(Collections.emptyList());
        BaseContext.inject(SpiKeys.COMMAND_BUS, CommandBus.class, prior);
        try {
            try (ApplicationContext context = ApplicationContext.run()) {
                assertEquals(context.getBean(CommandBus.class), BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow(AssertionError::new));
            }
            assertEquals(prior, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow(AssertionError::new));
        } finally {
            BaseContext.remove(SpiKeys.COMMAND_BUS);
        }
    }

    /**
     * 验证<code>context.getBeansOfType(CommandBus.class).size()</code> 等于 <code>1</code>，<code>bus.execute(new NativeCommand()).getData()</code> 等于 <code>"native"</code>，<code>bus.execute(new NativeCommand())</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void generatedHandlerDiscoveryPreservesSnapshotAndFailureIdentity() {
        try (ApplicationContext context = ApplicationContext.run(Collections.singletonMap("r09.command", true))) {
            CommandBus bus = context.getBean(CommandBus.class);
            assertEquals(1, context.getBeansOfType(CommandBus.class).size());
            assertEquals("native", bus.execute(new NativeCommand()).getData());
            CommandRegistry registry = context.getBean(CommandRegistry.class);
            registry.register(new CommandExecutor<LateCommand>() {
                @Override
                public Set<Class<? extends Command>> supportedCommands() {
                    return Collections.singleton(LateCommand.class);
                }

                @Override
                public R<?> execute(LateCommand command) {
                    return R.ok();
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage().contains(NativeCommand.class.getName())</code>为真。
     */
    @Test
    void generatedHandlerDiscoveryRejectsOverlappingCommands() {
        try (ApplicationContext context = ApplicationContext.run(Collections.singletonMap("r09.command", true))) {
            CommandRegistry registry = new CommandRegistry();
            registry.register(new CommandExecutor<Command>() {
                @Override
                public Set<Class<? extends Command>> supportedCommands() {
                    return new HashSet<>(Arrays.asList(NativeCommand.class, LateCommand.class));
                }

                @Override
                public R<?> execute(Command command) {
                    return R.ok();
                }
            });

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new MicronautCommandBus(context, registry));

            assertTrue(failure.getMessage().contains(NativeCommand.class.getName()));
        }
    }

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>。
     */
    @Test
    void explicitProjectionWithoutDependenciesFailsAtNativeStartup() {
        ApplicationContext context = ApplicationContext.builder()
                .properties(Collections.singletonMap(MicronautProjectionViewManager.ENABLED, true)).build();
        try {
            assertThrows(RuntimeException.class, context::start);
        } finally {
            context.close();
        }
    }

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>failure.getMessage().contains("durable positions")</code>为真。
     */
    @Test
    void explicitProjectionRejectsMemoryAndNoopBeforeScheduling() {
        ApplicationContext context = ApplicationContext.builder()
                .properties(Collections.singletonMap(MicronautProjectionViewManager.ENABLED, true)).build();
        context.registerSingleton(ProjectionPositionRepository.class, new InMemoryProjectionPositionRepository());
        context.registerSingleton(EventChunkReader.class, new NoopEventChunkReader<>());
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, context::start);
            assertTrue(failure.getMessage().contains("durable positions"));
        } finally {
            context.close();
        }
    }

    /**
     * 验证被测调用 抛出 <code>Throwable.class</code>，<code>failure</code> 与…为同一实例 <code>startup</code>，<code>startup.getSuppressed()[0]</code> 与…为同一实例 <code>firstCancel</code>。
     */
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

    /**
     * 验证<code>MicronautProjectionScheduler.CronExpression.parse("0 0 0 1 1 *").next(from)</code> 等于 <code>LocalDateTime.of(2027, 1, 1, 0, 0)</code>，<code>MicronautProjectionScheduler.CronExpression.parse("0 0 0 ? * *")</code> 抛出 <code>IllegalArgumentException.class</code>，<code>MicronautProjectionScheduler.CronExpression.parse("0/0 * * * * *")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
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

    /**
     * 在测试框架中执行 <code>TimeZone.getDefault()</code>、<code>TimeZone.setDefault()</code>、<code>TimeZone.getTimeZone()</code>，验证该场景。
     */
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

    /**
     * 实现 <code>EventChunkReader</code>，供测试调用 <code>read()</code> 并观察结果。
     */
    @Singleton
    @Requires(property = PARTIAL_START, value = "true")
    static class UnusedReader implements EventChunkReader<Object> {
        /**
         * 读取调用指定的数据或事件流。
         *
         * @param stream Redis Stream 的名称
         * @param from   用于此操作的 <code>from</code>（<code>long</code>）
         * @param size   用于此操作的 <code>size</code>（<code>int</code>）
         * @param types  用于此操作的 <code>types</code>（<code>Collection&lt;String&gt;</code>）
         * @return 事件块
         */
        @Override
        public EventChunk<Object> read(String stream, long from, int size, Collection<String> types) {
            throw new AssertionError("failed startup must not read events");
        }
    }

    /**
     * 模拟投影位点存储并控制写入行为。
     */
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
         * 创建 <code>new AssertionError</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new AssertionError("failed startup must not use projection positions")</code>
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
        /**
         * 当前失败路径所用或观察到的原始异常。
         */
        final IllegalArgumentException failure = new IllegalArgumentException("handler failure");
        /**
         * 令当前替身在指定测试路径中返回失败。
         */
        boolean fail;

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.singleton(NativeCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.singleton(NativeCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(NativeCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.ok("native")</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok("native")</code>
         */
        @Override
        public R<?> execute(NativeCommand command) {
            if (fail) {
                throw failure;
            }
            return R.ok("native");
        }
    }
}
