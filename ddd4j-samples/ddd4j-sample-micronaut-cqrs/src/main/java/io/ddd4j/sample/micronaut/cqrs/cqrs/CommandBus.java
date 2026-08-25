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
package io.ddd4j.sample.micronaut.cqrs.cqrs;

import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 简单命令总线（CQRS 写侧）。
 *
 * <p>注册命令类型到处理器的映射，执行命令时路由到对应处理器。
 */
public class CommandBus {

    private final Map<Class<?>, Function<?, ?>> handlers = new HashMap<>();

    public <C, R> void register(Class<C> commandType, Function<C, R> handler) {
        handlers.put(commandType, handler);
    }

    @SuppressWarnings("unchecked")
    public <C, R> R execute(C command) {
        Function<C, R> handler = (Function<C, R>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for command: " + command.getClass().getName());
        }
        return handler.apply(command);
    }
}
