package io.ddd4j.quarkus.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus command bus backed by the framework-neutral ddd4j command SPI.
 */
@Slf4j
@ApplicationScoped
@SuppressWarnings({"rawtypes", "unchecked"})
public class QuarkusCommandBus {

    private final Map<Class<? extends Command>, CommandExecutor<?>> executorMap = new ConcurrentHashMap<>();

    @Inject
    Instance<CommandExecutor<?>> executors;

    void onStart(@Observes StartupEvent event) {
        for (CommandExecutor<?> executor : executors) {
            for (Class<? extends Command> commandType : executor.supportedCommands()) {
                executorMap.put(commandType, executor);
                log.info("Registered command executor: {} -> {}", commandType.getName(), executor.getClass().getSimpleName());
            }
        }
        log.info("QuarkusCommandBus initialized with {} executors", executors.stream().count());
    }

    public Result<?> executeVoid(Command command) {
        return execute(command);
    }

    public <R> Result<R> execute(Command command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        CommandExecutor executor = findExecutor(command);
        if (Objects.isNull(executor)) {
            throw new IllegalStateException("No executor found for command: " + command.getClass().getName());
        }
        return (Result<R>) executor.execute(command);
    }

    private CommandExecutor<?> findExecutor(Command command) {
        CommandExecutor<?> executor = executorMap.get(command.getClass());
        if (Objects.nonNull(executor)) {
            return executor;
        }
        for (CommandExecutor<?> candidate : executors) {
            for (Class<? extends Command> commandType : candidate.supportedCommands()) {
                if (commandType.equals(command.getClass())) {
                    executorMap.put(commandType, candidate);
                    return candidate;
                }
            }
        }
        return null;
    }
}
