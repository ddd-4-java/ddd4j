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
 * Quarkus 命令总线。
 * <p>
 * 基于框架中立的 ddd4j 命令 SPI 实现，在启动时扫描所有 {@link CommandExecutor} Bean
 * 并构建命令类型到执行器的路由表。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
@SuppressWarnings({"rawtypes", "unchecked"})
public class QuarkusCommandBus {

    /** 命令类型到执行器的路由映射 */
    private final Map<Class<? extends Command>, CommandExecutor<?>> executorMap = new ConcurrentHashMap<>();

    /** CDI 命令执行器实例集 */
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

    /**
     * 执行命令（不关心返回值）。
     *
     * @param command 命令对象
     * @return 执行结果
     */
    public Result<?> executeVoid(Command command) {
        return execute(command);
    }

    /**
     * 执行命令并返回结果。
     *
     * @param command 命令对象
     * @param <R>     结果类型
     * @return 执行结果
     */
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
