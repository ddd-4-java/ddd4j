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

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.api.R;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guice command bus implementation.
 * <p>
 * Based on framework-neutral ddd4j command SPI, scans all {@link CommandExecutor} bindings
 * at startup and builds a command type to executor routing table.
 * <p>
 * Supports two registration sources:
 * <ul>
 *   <li>Classes implementing core {@code CommandExecutor} interface</li>
 *   <li>Classes annotated with {@code @io.ddd4j.guice.annotation.ddd.CommandExecutor}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public class GuiceCommandBus implements CommandBus {

    /**
     * Command type to executor routing map
     */
    private final Map<Class<? extends Command>, CommandExecutor<?>> executorMap = new ConcurrentHashMap<>();

    /**
     * Create a new GuiceCommandBus with the given executors.
     *
     * @param executors command executors to register
     */
    public GuiceCommandBus(Collection<CommandExecutor<?>> executors) {
        Objects.requireNonNull(executors, "executors must not be null");
        for (CommandExecutor<?> executor : executors) {
            register(executor);
        }
        log.info("GuiceCommandBus initialized with {} executors", executors.size());
    }

    /**
     * Register a command executor.
     *
     * @param executor the executor to register
     */
    private void register(CommandExecutor<?> executor) {
        CommandExecutor<?> actual = Objects.requireNonNull(executor, "executor must not be null");
        for (Class<? extends Command> commandType : actual.supportedCommands()) {
            CommandExecutor<?> previous = executorMap.putIfAbsent(commandType, actual);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException("Multiple executors found for command: " + commandType.getName());
            }
            log.info("Registered command executor: {} -> {}", commandType.getName(), actual.getClass().getSimpleName());
        }
    }

    /**
     * \u628a\u547d\u4ee4\u4ea4\u7ed9\u5df2\u7ecf\u5b8c\u6210 Guice \u88c5\u914d\u7684\u5171\u4eab\u6267\u884c\u5668\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param command \u8981\u4ea4\u7ed9\u5171\u4eab\u6838\u5fc3\u8def\u7531\u7684\u547d\u4ee4
     * @return \u5f53\u524d\u547d\u4ee4\u7684\u5904\u7406\u7ed3\u679c
     */
    @Override
    public <T> R<T> execute(Command command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        CommandExecutor executor = findExecutor(command);
        if (Objects.isNull(executor)) {
            throw new IllegalStateException("No executor found for command: " + command.getClass().getName());
        }
        return (R<T>) executor.execute(command);
    }

    /**
     * 执行 <code>find Executor</code>，调用 <code>executorMap.get</code>、<code>command.getClass</code>、<code>executorMap.values</code>。
     *
     * @param command 传入此操作的 <code>Command</code> 类型 <code>command</code>
     * @return 按条件返回 <code>executor</code>、<code>candidate</code>、<code>null</code>
     */
    private CommandExecutor<?> findExecutor(Command command) {
        CommandExecutor<?> executor = executorMap.get(command.getClass());
        if (Objects.nonNull(executor)) {
            return executor;
        }
        // defensive lookup for commands registered after initialization
        for (CommandExecutor<?> candidate : executorMap.values()) {
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
