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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.api.R;
import io.ddd4j.guice.Ddd4jCommandGuiceModule;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class GuiceCommandBusTest {

    /** \u9a8c\u8bc1<code>commandBus</code>\u975e\u7a7a\uff0c<code>result</code>\u975e\u7a7a\uff0c<code>executed.get()</code>\u4e3a\u771f\u3002 */
    @Test
    void commandBusRoutesToRegisteredExecutor() {
        AtomicBoolean executed = new AtomicBoolean(false);
        TestCommandExecutor executor = new TestCommandExecutor(executed);
/**
 * Guice.createInjector() 方法。
 * @param Ddd4jCommandGuiceModule() Ddd4jCommandGuiceModule() 参数。
 * @param } } 参数。
 * @return 方法执行结果。
 */

        Injector injector = Guice.createInjector(
                new Ddd4jCommandGuiceModule(),
                new AbstractModule() {
                    /**
                     * configure() 方法。
                     */
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

    /** \u9a8c\u8bc1<code>commandBus</code>\u975e\u7a7a\uff0c<code>commandBus.execute(command)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void commandBusThrowsForUnregisteredCommand() {
        Injector injector = Guice.createInjector(new Ddd4jCommandGuiceModule());
        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        UnregisteredCommand command = new UnregisteredCommand();
        assertThrows(IllegalStateException.class, () -> commandBus.execute(command));
    }

    /** \u9a8c\u8bc1<code>commandBus</code>\u975e\u7a7a\uff0c<code>commandBus.execute(null)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void commandBusThrowsForNullCommand() {
        Injector injector = Guice.createInjector(new Ddd4jCommandGuiceModule());
        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        assertThrows(IllegalArgumentException.class, () -> commandBus.execute(null));
    }

    /** \u9a8c\u8bc1<code>commandBus</code>\u975e\u7a7a\uff0c<code>testExecuted.get()</code>\u4e3a\u771f\uff0c<code>anotherExecuted.get()</code>\u4e3a\u771f\u3002 */
    @Test
    void commandBusWithMultipleExecutors() {
        AtomicBoolean testExecuted = new AtomicBoolean(false);
        AtomicBoolean anotherExecuted = new AtomicBoolean(false);

        Injector injector = Guice.createInjector(
                new Ddd4jCommandGuiceModule(),
                new AbstractModule() {
                    /**
                     * configure() 方法。
                     */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void commandBusDuplicateExecutorThrowsException() {
        TestCommandExecutor executor1 = new TestCommandExecutor(new AtomicBoolean());
        TestCommandExecutor executor2 = new TestCommandExecutor(new AtomicBoolean());

        // GuiceCommandBus constructor throws when duplicate command types are registered
        assertThrows(IllegalStateException.class, () -> new GuiceCommandBus(java.util.Arrays.asList(executor1, executor2)));
    }

    // Test command classes

    /**
     * 已注册执行器的测试命令，用于验证命令路由到对应执行器。
     */
    private static class TestCommand implements Command {
    }

    /**
     * 第二种测试命令，用于验证多执行器并存时的分发。
     */
    private static class AnotherCommand implements Command {
    }

    /**
     * 未注册执行器的测试命令，用于验证命令总线对未知命令的异常处理。
     */
    private static class UnregisteredCommand implements Command {
    }

    // Test executor classes

    /**
     * {@link TestCommand} 的执行器桩，执行时置位标记以供断言。
     */
    private static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        /** \u6807\u8bb0\u6d4b\u8bd5\u56de\u8c03\u6216\u4e1a\u52a1\u64cd\u4f5c\u5df2\u7ecf\u5b9e\u9645\u6267\u884c\u3002 */
        private final AtomicBoolean executed;

        /**
         * \u8bbe\u7f6e <code>executed</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestCommandExecutor</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param executed \u7528\u4e8e\u521d\u59cb\u5316 <code>TestCommandExecutor</code> \u7684 <code>AtomicBoolean</code> \u503c
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
         * 执行处理逻辑。
         * @param command command 参数。
         * @return 方法执行结果。
         */
        @Override
        public R<?> execute(TestCommand command) {
            executed.set(true);
            return R.ok();
        }
    }

    /**
     * {@link AnotherCommand} 的执行器桩，执行时置位标记以供断言。
     */
    private static class AnotherCommandExecutor implements CommandExecutor<AnotherCommand> {

        /** \u6807\u8bb0\u6d4b\u8bd5\u56de\u8c03\u6216\u4e1a\u52a1\u64cd\u4f5c\u5df2\u7ecf\u5b9e\u9645\u6267\u884c\u3002 */
        private final AtomicBoolean executed;

        /**
         * \u8bbe\u7f6e <code>executed</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>AnotherCommandExecutor</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param executed \u7528\u4e8e\u521d\u59cb\u5316 <code>AnotherCommandExecutor</code> \u7684 <code>AtomicBoolean</code> \u503c
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
         * 执行处理逻辑。
         * @param command command 参数。
         * @return 方法执行结果。
         */
        @Override
        public R<?> execute(AnotherCommand command) {
            executed.set(true);
            return R.ok();
        }
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
}
