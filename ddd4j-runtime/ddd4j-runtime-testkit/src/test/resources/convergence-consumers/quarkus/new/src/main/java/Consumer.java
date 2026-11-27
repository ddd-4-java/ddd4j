/**
 * 独立消费者公开类型编译闭包。
 */
public final class Consumer {
    /**
     * 根据命令类型分发写操作的命令总线。
     */
    io.ddd4j.core.cqrs.command.CommandBus command;
    /**
     * 交给运行时注册和调度的投影视图。
     */
    io.ddd4j.core.cqrs.readmodel.ViewManager views;
    /**
     * 当前消费者方案所使用的运行时实例。
     */
    io.ddd4j.quarkus.Ddd4jQuarkusRuntime runtime;
}
