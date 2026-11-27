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

import io.ddd4j.core.cqrs.command.*;
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

    /** \u6846\u67b6\u5bb9\u5668\u4e2d\u9700\u8981\u767b\u8bb0\u5230\u547d\u4ee4\u603b\u7ebf\u7684\u6267\u884c\u5668\u3002 */
    @Inject
    Instance<CommandExecutor<?>> executors;

    /** \u7528 CDI \u6536\u96c6\u7684\u6267\u884c\u5668\u88c5\u914d\u7684\u5171\u4eab\u547d\u4ee4\u603b\u7ebf\u3002 */
    private volatile DefaultCommandBus delegate = createDelegate(List.of());

    /**
     * \u5728\u5e94\u7528\u542f\u52a8\u540e\u6536\u96c6 CDI \u6267\u884c\u5668\uff0c\u5e76\u521b\u5efa\u5171\u4eab\u547d\u4ee4\u603b\u7ebf\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void onStart(@Observes @jakarta.annotation.Priority(900) StartupEvent event) {
        List<CommandExecutor<?>> snapshot = new java.util.ArrayList<>();
        executors.forEach(snapshot::add);
        delegate = createDelegate(snapshot);
    }

    /**
     * \u6839\u636e CDI \u5feb\u7167\u88c5\u914d\u5171\u4eab\u547d\u4ee4\u603b\u7ebf\u3002
     * @param snapshot \u4ece CDI \u5bb9\u5668\u53d6\u5f97\u7684\u6267\u884c\u5668\u5feb\u7167
     * @return \u7ed1\u5b9a\u5f53\u524d\u6267\u884c\u5668\u7684\u5171\u4eab\u547d\u4ee4\u603b\u7ebf
     */
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
