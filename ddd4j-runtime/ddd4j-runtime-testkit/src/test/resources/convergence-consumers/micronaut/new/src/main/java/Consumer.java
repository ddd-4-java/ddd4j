/**
 * 独立消费者公开类型编译闭包。
 */
public final class Consumer {
    /** \u6839\u636e\u547d\u4ee4\u7c7b\u578b\u5206\u53d1\u5199\u64cd\u4f5c\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
    io.ddd4j.core.cqrs.command.CommandBus command;
    /** \u4ea4\u7ed9\u8fd0\u884c\u65f6\u6ce8\u518c\u548c\u8c03\u5ea6\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    io.ddd4j.core.cqrs.readmodel.ViewManager views;
    /** \u5f53\u524d\u6d88\u8d39\u8005\u65b9\u6848\u6240\u4f7f\u7528\u7684\u8fd0\u884c\u65f6\u5b9e\u4f8b\u3002 */
    io.ddd4j.micronaut.Ddd4jMicronautRuntime runtime;
}
