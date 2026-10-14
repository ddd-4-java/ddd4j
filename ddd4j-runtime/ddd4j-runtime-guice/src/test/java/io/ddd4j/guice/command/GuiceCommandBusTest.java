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
import io.ddd4j.core.cqrs.command.Result;
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
        Result<?> result = commandBus.execute(command);

        assertNotNull(result);
        assertTrue(executed.get());
    }

    @Test
    void commandBusThrowsForUnregisteredCommand() {
        Injector injector = Guice.createInjector(new Ddd4jCommandGuiceModule());
        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        UnregisteredCommand command = new UnregisteredCommand();
        assertThrows(IllegalStateException.class, () -> commandBus.execute(command));
    }

    @Test
    void commandBusThrowsForNullCommand() {
        Injector injector = Guice.createInjector(new Ddd4jCommandGuiceModule());
        CommandBus commandBus = injector.getInstance(CommandBus.class);
        assertNotNull(commandBus);

        assertThrows(IllegalArgumentException.class, () -> commandBus.execute(null));
    }

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

        private final AtomicBoolean executed;

        TestCommandExecutor(AtomicBoolean executed) {
            this.executed = executed;
        }

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
        public Result execute(TestCommand command) {
            executed.set(true);
            return Result.ok();
        }
    }

    /**
     * {@link AnotherCommand} 的执行器桩，执行时置位标记以供断言。
     */
    private static class AnotherCommandExecutor implements CommandExecutor<AnotherCommand> {

        private final AtomicBoolean executed;

        AnotherCommandExecutor(AtomicBoolean executed) {
            this.executed = executed;
        }

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
        public Result execute(AnotherCommand command) {
            executed.set(true);
            return Result.ok();
        }
    }
}
