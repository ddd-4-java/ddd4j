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
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.guice.Ddd4jCommandGuiceModule;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>GuiceCommandBus</code> \u5728<code>commandBus</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class GuiceCommandBusTest {

    /** \u9a8c\u8bc1<code>commandBus</code>\u975e\u7a7a\uff0c<code>result</code>\u975e\u7a7a\uff0c<code>executed.get()</code>\u4e3a\u771f\u3002 */
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
        Result<?> result = commandBus.execute(command);

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
    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    private static class TestCommand implements Command {
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    private static class AnotherCommand implements Command {
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    private static class UnregisteredCommand implements Command {
    }

    // Test executor classes
    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
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
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>executed.set(true)</code>、<code>Result.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>Result.ok()</code>
         */
        @Override
        public Result execute(TestCommand command) {
            executed.set(true);
            return Result.ok();
        }
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
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
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>executed.set(true)</code>、<code>Result.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>Result.ok()</code>
         */
        @Override
        public Result execute(AnotherCommand command) {
            executed.set(true);
            return Result.ok();
        }
    }
}
