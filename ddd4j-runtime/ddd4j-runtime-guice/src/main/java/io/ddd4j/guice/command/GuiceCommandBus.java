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
import io.ddd4j.core.cqrs.command.Result;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@SuppressWarnings({"rawtypes", "unchecked"})
public class GuiceCommandBus implements CommandBus {

    private static final Logger log = LoggerFactory.getLogger(GuiceCommandBus.class);

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
