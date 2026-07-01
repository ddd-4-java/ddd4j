package io.ddd4j.quarkus.core.command;

import io.ddd4j.core.ddd.command.DddCommandExecutor;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandExecutor;
import org.fuin.cqrs4j.core.Result;
import org.fuin.ddd4j.core.EventType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus 命令总线（CDI 实现）。
 */
@Slf4j
@ApplicationScoped
public class QuarkusCommandBus {

    private final Map<String, CommandExecutor<Void, Result<?>, ?>> executorMap = new ConcurrentHashMap<>();

    @Inject
    Instance<DddCommandExecutor<?>> executors;

    void onStart(@Observes StartupEvent event) {
        for (DddCommandExecutor<?> executor : executors) {
            Set<EventType> commandTypes = executor.getCommandTypes();
            for (EventType commandType : commandTypes) {
                executorMap.put(commandType.asString(), executor);
                log.info("Registered command executor: {} -> {}", commandType.asString(), executor.getClass().getSimpleName());
            }
        }
        log.info("QuarkusCommandBus initialized with {} executors", executors.stream().count());
    }

    public Result<?> executeVoid(Command command) throws CommandExecutionFailedException {
        return execute(command);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <R> Result<R> execute(Command command) throws CommandExecutionFailedException {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        CommandExecutor<Void, Result<?>, ?> executor = findExecutor(command);
        if (Objects.isNull(executor)) {
            throw new CommandExecutionFailedException(
                    new IllegalStateException("No executor found for command: " + command.getClass().getName()));
        }

        try {
            Object raw = ((CommandExecutor) executor).execute(null, command);
            return (Result<R>) raw;
        } catch (CommandExecutionFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommandExecutionFailedException(ex);
        }
    }

    private CommandExecutor<Void, Result<?>, ?> findExecutor(Command command) {
        CommandExecutor<Void, Result<?>, ?> executor = executorMap.get(command.getClass().getSimpleName());
        if (Objects.nonNull(executor)) {
            return executor;
        }
        for (DddCommandExecutor<?> candidate : executors) {
            for (EventType commandType : candidate.getCommandTypes()) {
                if (commandType.asString().equals(command.getClass().getSimpleName())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
