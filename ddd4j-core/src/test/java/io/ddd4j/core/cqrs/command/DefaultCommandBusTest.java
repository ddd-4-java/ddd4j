package io.ddd4j.core.cqrs.command;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCommandBusTest {

    @Test
    void shouldRouteRegisteredCommand() {
        DefaultCommandBus bus = new DefaultCommandBus(Collections.<CommandExecutor<?>>singletonList(new GreetingExecutor()));

        assertEquals("hello ddd4j", bus.execute(new GreetingCommand("ddd4j")).getData());
    }

    @Test
    void shouldRejectMissingAndDuplicateExecutors() {
        DefaultCommandBus bus = new DefaultCommandBus(Collections.<CommandExecutor<?>>singletonList(new GreetingExecutor()));
        assertThrows(IllegalStateException.class, () -> bus.execute(new UnknownCommand()));
        assertThrows(IllegalStateException.class, () -> new DefaultCommandBus(Arrays.<CommandExecutor<?>>asList(
                new GreetingExecutor(), new DuplicateGreetingExecutor())));
    }

    private static final class GreetingCommand implements Command {
        private final String name;
        private GreetingCommand(String name) { this.name = name; }
    }
    private static final class UnknownCommand implements Command { }
    private static class GreetingExecutor implements CommandExecutor<GreetingCommand> {
        @Override public Result<String> execute(GreetingCommand command) { return Result.ok("hello " + command.name); }
        @Override public Set<Class<? extends Command>> supportedCommands() { return Collections.<Class<? extends Command>>singleton(GreetingCommand.class); }
    }
    private static final class DuplicateGreetingExecutor extends GreetingExecutor { }
}
