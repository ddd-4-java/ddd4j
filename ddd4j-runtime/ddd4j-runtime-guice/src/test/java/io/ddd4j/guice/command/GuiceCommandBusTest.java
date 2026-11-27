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
package io.ddd4j.guice.command;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.guice.Ddd4jCommandGuiceModule;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>GuiceCommandBus</code> 在<code>commandBus</code>等操作中的行为契约。
 */
class GuiceCommandBusTest {

    /**
     * 验证<code>commandBus</code>非空，<code>result</code>非空，<code>executed.get()</code>为真。
     */
    @Test
    void commandBusRoutesToRegisteredExecutor() {
        AtomicBoolean executed = new AtomicBoolean(false);
        TestCommandExecutor executor = new TestCommandExecutor(executed);

        Injector injector = Guice.createInjector(
                new Ddd4jCommandGuiceModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(TestCommandExecutor.class).toInstance(executor);
                    }
                }
        );

        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        TestCommand command = new TestCommand();
        R<?> result = commandBus.execute(command);

        assertNotNull(result);
        assertTrue(executed.get());
    }

    /**
     * 验证<code>commandBus</code>非空，<code>commandBus.execute(command)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void commandBusThrowsForUnregisteredCommand() {
        Injector injector = Guice.createInjector(new Ddd4jCommandGuiceModule());
        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        UnregisteredCommand command = new UnregisteredCommand();
        assertThrows(IllegalStateException.class, () -> commandBus.execute(command));
    }

    /**
     * 验证<code>commandBus</code>非空，<code>commandBus.execute(null)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void commandBusThrowsForNullCommand() {
        Injector injector = Guice.createInjector(new Ddd4jCommandGuiceModule());
        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        assertThrows(IllegalArgumentException.class, () -> commandBus.execute(null));
    }

    /**
     * 验证<code>commandBus</code>非空，<code>testExecuted.get()</code>为真，<code>anotherExecuted.get()</code>为真。
     */
    @Test
    void commandBusWithMultipleExecutors() {
        AtomicBoolean testExecuted = new AtomicBoolean(false);
        AtomicBoolean anotherExecuted = new AtomicBoolean(false);

        Injector injector = Guice.createInjector(
                new Ddd4jCommandGuiceModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(TestCommandExecutor.class).toInstance(new TestCommandExecutor(testExecuted));
                        bind(AnotherCommandExecutor.class).toInstance(new AnotherCommandExecutor(anotherExecuted));
                    }
                }
        );

        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        commandBus.execute(new TestCommand());
        assertTrue(testExecuted.get());

        commandBus.execute(new AnotherCommand());
        assertTrue(anotherExecuted.get());
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void commandBusDuplicateExecutorThrowsException() {
        TestCommandExecutor executor1 = new TestCommandExecutor(new AtomicBoolean());
        TestCommandExecutor executor2 = new TestCommandExecutor(new AtomicBoolean());

        // GuiceCommandBus constructor throws when duplicate command types are registered
        assertThrows(IllegalStateException.class, () -> new GuiceCommandBus(java.util.Arrays.asList(executor1, executor2)));
    }

    /**
     * 验证<code>bus.execute(new AnotherCommand())</code> 与…为同一实例 <code>expected</code>，<code>bus.execute(new TestCommand())</code> 与…为同一实例 <code>expected</code>。
     */
    @Test
    void dynamicDiscoveryCachesRoutesAndPreservesResultIdentity() {
        Set<Class<? extends Command>> types = new java.util.LinkedHashSet<>();
        types.add(TestCommand.class);
        R<?> expected = R.ok("dynamic");
        CommandExecutor<Command> executor = new CommandExecutor<>() {
            public Set<Class<? extends Command>> supportedCommands() {
                return types;
            }

            public R<?> execute(Command command) {
                return expected;
            }
        };
        GuiceCommandBus bus = new GuiceCommandBus(java.util.List.of(executor));
        types.add(AnotherCommand.class);
        assertSame(expected, bus.execute(new AnotherCommand()));
        types.clear();
        assertSame(expected, bus.execute(new AnotherCommand()));
        assertSame(expected, bus.execute(new TestCommand()));
    }

    /**
     * 验证<code>io.ddd4j.core.cqrs.command.DefaultCommandBus.class.isAssignableFrom(GuiceCommandBus.class)</code>为真。
     */
    @Test
    void routingUsesSharedCommandEngine() {
        assertTrue(io.ddd4j.core.cqrs.command.DefaultCommandBus.class.isAssignableFrom(GuiceCommandBus.class));
    }

    /**
     * 验证<code>bus.execute(new TestCommand())</code> 抛出 <code>IllegalStateException.class</code>，被测调用 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void initiallyEmptyExecutorsStayUndiscoverableAndExceptionsAreUnchanged() {
        Set<Class<? extends Command>> empty = new java.util.HashSet<>();
        CommandExecutor<Command> absent = new CommandExecutor<>() {
            public Set<Class<? extends Command>> supportedCommands() {
                return empty;
            }

            public R<?> execute(Command command) {
                throw new AssertionError("must stay undiscovered");
            }
        };
        GuiceCommandBus bus = new GuiceCommandBus(java.util.List.of(absent));
        empty.add(TestCommand.class);
        assertThrows(IllegalStateException.class, () -> bus.execute(new TestCommand()));
        IllegalStateException failure = new IllegalStateException("business failure");
        CommandExecutor<TestCommand> throwing = new CommandExecutor<>() {
            public Set<Class<? extends Command>> supportedCommands() {
                return Set.of(TestCommand.class);
            }

            public R<?> execute(TestCommand command) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new GuiceCommandBus(java.util.List.of(throwing)).execute(new TestCommand())));
    }

    /**
     * 验证<code>calls.get()</code> 等于 <code>1</code>，<code>bus.execute(new AnotherCommand()).isOk()</code>为真，<code>bus.execute(new TestCommand()).isOk()</code>为真。
     */
    @Test
    void initialSupportedCommandsIsReadOnlyOnceBeforeDynamicMiss() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CommandExecutor<Command> executor = new CommandExecutor<>() {
            public Set<Class<? extends Command>> supportedCommands() {
                return calls.incrementAndGet() == 1 ? Set.of(TestCommand.class) : Set.of(AnotherCommand.class);
            }

            public R<?> execute(Command command) {
                return R.ok();
            }
        };
        GuiceCommandBus bus = new GuiceCommandBus(java.util.List.of(executor));
        assertEquals(1, calls.get());
        assertTrue(bus.execute(new AnotherCommand()).isOk());
        assertEquals(2, calls.get());
        assertTrue(bus.execute(new TestCommand()).isOk());
        assertEquals(2, calls.get());
    }

    // Test command classes

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    private static class TestCommand implements Command {
    }

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    private static class AnotherCommand implements Command {
    }

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    private static class UnregisteredCommand implements Command {
    }

    // Test executor classes

    /**
     * 接收测试命令并返回可断言结果的执行器替身。
     */
    private static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        /**
         * 标记测试回调或业务操作已经实际执行。
         */
        private final AtomicBoolean executed;

        /**
         * 设置 <code>executed</code>，用于构造 <code>TestCommandExecutor</code> 测试实例。
         *
         * @param executed 用于初始化 <code>TestCommandExecutor</code> 的 <code>AtomicBoolean</code> 值
         */
        TestCommandExecutor(AtomicBoolean executed) {
            this.executed = executed;
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.singleton(TestCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.singleton(TestCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(TestCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>executed.set(true)</code>、<code>R.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok()</code>
         */
        @Override
        public R<?> execute(TestCommand command) {
            executed.set(true);
            return R.ok();
        }
    }

    /**
     * 接收测试命令并返回可断言结果的执行器替身。
     */
    private static class AnotherCommandExecutor implements CommandExecutor<AnotherCommand> {

        /**
         * 标记测试回调或业务操作已经实际执行。
         */
        private final AtomicBoolean executed;

        /**
         * 设置 <code>executed</code>，用于构造 <code>AnotherCommandExecutor</code> 测试实例。
         *
         * @param executed 用于初始化 <code>AnotherCommandExecutor</code> 的 <code>AtomicBoolean</code> 值
         */
        AnotherCommandExecutor(AtomicBoolean executed) {
            this.executed = executed;
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.singleton(AnotherCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.singleton(AnotherCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(AnotherCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>executed.set(true)</code>、<code>R.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok()</code>
         */
        @Override
        public R<?> execute(AnotherCommand command) {
            executed.set(true);
            return R.ok();
        }
    }
}
