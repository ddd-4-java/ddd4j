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
package io.ddd4j.quarkus.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
@DefaultBean
@SuppressWarnings({"rawtypes", "unchecked"})
public class QuarkusCommandBus implements CommandBus {

    @Inject
    Instance<CommandExecutor<?>> executors;

    private volatile DefaultCommandBus delegate = createDelegate(List.of());

    void onStart(@Observes @jakarta.annotation.Priority(900) StartupEvent event) {
        List<CommandExecutor<?>> snapshot = new java.util.ArrayList<>();
        executors.forEach(snapshot::add);
        delegate = createDelegate(snapshot);
    }

    private DefaultCommandBus createDelegate(Collection<CommandExecutor<?>> snapshot) {
        return new DefaultCommandBus(snapshot) {
            @Override
            protected CommandExecutor<?> resolveMissingExecutor(Class<? extends Command> commandType,
                    Collection<CommandExecutor<?>> registeredExecutors) {
                // ArC 的 Instance 可发现启动后才支持该命令的执行器；缓存和执行仍由 core 负责。
                if (Objects.nonNull(executors)) {
                    for (CommandExecutor<?> candidate : executors) {
                        if (candidate.supportedCommands().contains(commandType)) {
                            return candidate;
                        }
                    }
                }
                return null;
            }
        };
    }

    /**
     * 执行命令（不关心返回值）。
     *
     * @param command 命令对象
     * @return 执行结果
     */
    @ActivateRequestContext
    public Result<?> executeVoid(Command command) {
        return execute(command);
    }

    /**
     * 在 CDI 请求上下文中执行命令并返回结果。
     *
     * @param command 命令对象
     * @param <R>     结果类型
     * @return 执行结果
     */
    @Override
    @ActivateRequestContext
    public <R> Result<R> execute(Command command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        return delegate.execute(command);
    }
}
