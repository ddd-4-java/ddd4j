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
