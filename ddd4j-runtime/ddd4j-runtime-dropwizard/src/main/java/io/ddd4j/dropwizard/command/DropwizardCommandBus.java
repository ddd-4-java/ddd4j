package io.ddd4j.dropwizard.command;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.dropwizard.core.Application;

import java.util.Collection;
import java.util.Objects;

/**
 * Dropwizard 命令装配；路由与异常完全复用共享总线，事务归应用执行器。
 */
public class DropwizardCommandBus extends DefaultCommandBus {
    /**
     * 对执行器一次性注册并建立快照，供 Bundle 和兼容入口使用。
     *
     * @param executors 执行器候选集合
     */
    public DropwizardCommandBus(Collection<? extends CommandExecutor<?>> executors) {
        super(registered(executors));
    }

    /**
     * 从显式传入的执行器建立已注册的命令总线。
     *
     * @param executors 当前框架容器提供的命令执行器
     * @return 完成执行器登记的 Dropwizard 命令总线
     */
    private static Collection<CommandExecutor<?>> registered(Collection<? extends CommandExecutor<?>> executors) {
        Objects.requireNonNull(executors, "executors must not be null");
        CommandRegistry registry = new CommandRegistry();
        for (CommandExecutor<?> executor : executors) {
            registry.register(executor);
        }
        return registry.executors();
    }

    /**
     * 在原生应用装配期创建命令总线，不自动扫描或添加事务。
     *
     * @param app       应用装配锚点
     * @param executors 执行器候选集合
     * @return 完成注册的总线
     */
    public static DropwizardCommandBus create(Application<?> app, Collection<CommandExecutor<?>> executors) {
        Objects.requireNonNull(app, "app must not be null");
        return new DropwizardCommandBus(executors);
    }
}
