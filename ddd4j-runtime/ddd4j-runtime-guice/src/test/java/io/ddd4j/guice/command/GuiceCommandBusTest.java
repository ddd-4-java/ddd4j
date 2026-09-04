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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiceCommandBusTest {

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
    private static class TestCommand implements Command {
    }

    private static class AnotherCommand implements Command {
    }

    private static class UnregisteredCommand implements Command {
    }

    // Test executor classes
    private static class TestCommandExecutor implements CommandExecutor<TestCommand> {

        private final AtomicBoolean executed;

        TestCommandExecutor(AtomicBoolean executed) {
            this.executed = executed;
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(TestCommand.class);
        }

        @Override
        public Result execute(TestCommand command) {
            executed.set(true);
            return Result.ok();
        }
    }

    private static class AnotherCommandExecutor implements CommandExecutor<AnotherCommand> {

        private final AtomicBoolean executed;

        AnotherCommandExecutor(AtomicBoolean executed) {
            this.executed = executed;
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(AnotherCommand.class);
        }

        @Override
        public Result execute(AnotherCommand command) {
            executed.set(true);
            return Result.ok();
        }
    }
}
