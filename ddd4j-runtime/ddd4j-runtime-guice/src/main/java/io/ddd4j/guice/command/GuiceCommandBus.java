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

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;

import java.util.Collection;
import java.util.Objects;

/**
 * Guice 执行器发现适配器；精确类型路由、缓存和执行均由共享总线拥有。
 */
public class GuiceCommandBus extends DefaultCommandBus {

    /**
     * 收集已有执行器并建立共享快照；初始没有路由的执行器不参加旧式动态补查。
     *
     * @param executors Guice 已发现执行器
     */
    public GuiceCommandBus(Collection<CommandExecutor<?>> executors) {
        super(Objects.requireNonNull(executors, "executors must not be null"));
    }

    /**
     * 把命令交给已经完成 Guice 装配的共享执行器。
     *
     * @param <T>     当前容器处理的组件或事件类型
     * @param command 要交给共享核心路由的命令
     * @return 当前命令的处理结果
     */
    @Override
    public <T> R<T> execute(Command command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        return super.execute(command);
    }

    /**
     * 为已有动态发现平台提供缺失路由钩子；默认快照模式不发现新执行器。
     *
     * <p>此实现调用 <code>candidate.supportedCommands().contains(commandType)</code>。</p>
     *
     * @param commandType         未缓存的命令精确类型
     * @param registeredExecutors 当前路由中执行器的只读实时视图，禁止结构修改
     * @return 根据执行路径返回<code>candidate</code>、<code>null</code>
     */
    @Override
    protected CommandExecutor<?> resolveMissingExecutor(Class<? extends Command> commandType,
                                                        Collection<CommandExecutor<?>> registeredExecutors) {
        for (CommandExecutor<?> candidate : registeredExecutors) {
            if (candidate.supportedCommands().contains(commandType)) {
                return candidate;
            }
        }
        return null;
    }
}
