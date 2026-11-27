package io.ddd4j.quarkus.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
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
public class QuarkusCommandBus implements CommandBus {

    /**
     * 命令类型到执行器的路由映射
     */
    private final Map<Class<? extends Command>, CommandExecutor<?>> executorMap = new ConcurrentHashMap<>();

    /**
     * CDI 命令执行器实例集
     */
    @Inject
    Instance<CommandExecutor<?>> executors;

    /**
     * \u5728\u5e94\u7528\u542f\u52a8\u540e\u6536\u96c6 CDI \u6267\u884c\u5668\uff0c\u5e76\u521b\u5efa\u5171\u4eab\u547d\u4ee4\u603b\u7ebf\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
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
    @Override
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

    /**
     * 执行 <code>find Executor</code>，调用 <code>executorMap.get</code>、<code>command.getClass</code>、<code>candidate.supportedCommands</code>。
     *
     * @param command 传入此操作的 <code>Command</code> 类型 <code>command</code>
     * @return 按条件返回 <code>executor</code>、<code>candidate</code>、<code>null</code>
     */
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
