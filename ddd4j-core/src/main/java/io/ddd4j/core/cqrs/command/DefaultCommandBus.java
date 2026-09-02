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
package io.ddd4j.core.cqrs.command;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于显式执行器集合的默认命令路由实现。
 * <p>
 * 阶段 6（ADR-0004）：本类是命令分发的唯一实现，运行时适配器
 * （{@code ddd4j-data-cqrs-spring}／{@code ddd4j-data-cqrs-quarkus}／...）
 * 以<b>继承</b>方式组装——先在自身构造器内完成执行器收集与
 * {@code io.ddd4j.data.cqrs.CommandRegistry} 注册，再以
 * {@code super(registry.executors())} 传入本类；适配器不复制路由逻辑，
 * 也不应 override {@link #execute}（事务等横切用类级注解让容器代理处理）。
 *
 * <h3>构造即快照（适配器装配顺序约束）</h3>
 * <p>
 * 本类构造时对执行器集合<b>一次性快照</b>（逐个 {@code putIfAbsent}，
 * 冲突即抛 {@link IllegalStateException}），之后对传入集合的任何变更都不会
 * 回灌到已构造的总线——因此适配器的收集／注册必须<b>全部发生在
 * {@code super(...)} 调用之前</b>（不可用容器延迟回调（如
 * {@code SmartInitializingSingleton}）注册）。为此本类自 2.0.x 起不再
 * {@code final}：仅放开继承组装点，快照语义与路由逻辑不变。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultCommandBus implements CommandBus {

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
