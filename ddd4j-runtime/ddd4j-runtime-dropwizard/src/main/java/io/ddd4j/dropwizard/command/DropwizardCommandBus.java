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
     * \u4ece\u663e\u5f0f\u4f20\u5165\u7684\u6267\u884c\u5668\u5efa\u7acb\u5df2\u6ce8\u518c\u7684\u547d\u4ee4\u603b\u7ebf\u3002
     * @param executors \u5f53\u524d\u6846\u67b6\u5bb9\u5668\u63d0\u4f9b\u7684\u547d\u4ee4\u6267\u884c\u5668
     * @return \u5b8c\u6210\u6267\u884c\u5668\u767b\u8bb0\u7684 Dropwizard \u547d\u4ee4\u603b\u7ebf
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
