package io.ddd4j.core.cqrs.command;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于显式执行器集合的默认命令路由实现。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class DefaultCommandBus implements CommandBus {

    private final Map<Class<? extends Command>, CommandExecutor<?>> executors = new ConcurrentHashMap<>();

    public DefaultCommandBus(Collection<? extends CommandExecutor<?>> commandExecutors) {
        Objects.requireNonNull(commandExecutors, "commandExecutors must not be null").forEach(this::register);
    }

    @Override
    public <R> Result<R> execute(Command command) {
        Command actual = Objects.requireNonNull(command, "command must not be null");
        CommandExecutor executor = executors.get(actual.getClass());
        if (Objects.isNull(executor)) {
            throw new IllegalStateException("No executor found for command: " + actual.getClass().getName());
        }
        return (Result<R>) executor.execute(actual);
    }

    private void register(CommandExecutor<?> executor) {
        CommandExecutor<?> actual = Objects.requireNonNull(executor, "executor must not be null");
        for (Class<? extends Command> commandType : actual.supportedCommands()) {
            CommandExecutor<?> previous = executors.putIfAbsent(commandType, actual);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException("Multiple executors found for command: " + commandType.getName());
            }
        }
    }
}
