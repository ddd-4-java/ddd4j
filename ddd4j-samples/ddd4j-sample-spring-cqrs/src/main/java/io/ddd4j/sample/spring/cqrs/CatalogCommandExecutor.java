package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Set;
import org.springframework.core.env.Environment;

/** \u5c06\u5171\u7528\u5546\u54c1\u4ea4\u6613\u547d\u4ee4\u5904\u7406\u5668\u6ce8\u518c\u5230\u793a\u4f8b\u8fd0\u884c\u6846\u67b6\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
@org.springframework.stereotype.Component
public class CatalogCommandExecutor implements CommandExecutor<Command> {
    /** \u4e0e\u5177\u4f53\u6846\u67b6\u65e0\u5173\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c\u3002 */
    private final CatalogApi api;
    /** \u6267\u884c\u5171\u7528\u5546\u54c1\u4ea4\u6613\u547d\u4ee4\u7684\u5904\u7406\u5668\u3002 */
    private final CommandExecutor<Command> delegate;

    /**
     * \u4f7f\u7528\u8fd0\u884c\u6846\u67b6\u63d0\u4f9b\u7684\u6570\u636e\u5e93\u914d\u7f6e\u548c\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668\u521d\u59cb\u5316\u4ea4\u6613\u547d\u4ee4\u6267\u884c\u5668\u3002
     * @param environment \u63d0\u4f9b\u6570\u636e\u5e93\u8fde\u63a5\u914d\u7f6e\u7684 Spring \u73af\u5883
     * @param publisher \u8d1f\u8d23\u5c06\u4ea4\u6613\u9886\u57df\u4e8b\u4ef6\u8f6c\u53d1\u7ed9\u8fd0\u884c\u6846\u67b6\u7684\u53d1\u5e03\u5668
     */
    public CatalogCommandExecutor(Environment environment, ApplicationEventPublisher publisher) {
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
        delegate = CatalogCqrs.executor(api);
    }

    /**
     * \u8fd4\u56de\u5df2\u5b8c\u6210\u88c5\u914d\u7684\u5171\u7528\u4ea4\u6613\u5165\u53e3\uff0c\u4f9b HTTP \u9002\u914d\u5668\u8bfb\u53d6\u5546\u54c1\u53ca\u8ba2\u5355\u3002
     * @return \u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    public CatalogApi api() {
        return api;
    }

    /**
     * 声明当前处理器可以接收的命令类型。
     *
     * <p>此实现调用 <code>delegate.supportedCommands()</code>。</p>
     *
     * @return 当前处理器声明可接收的命令类型集合
     */
    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return delegate.supportedCommands();
    }

    /**
     * \u5c06\u5546\u54c1\u4ea4\u6613\u547d\u4ee4\u4ea4\u7ed9\u5171\u7528\u7684\u547d\u4ee4\u6267\u884c\u5668\u3002
     * @param command \u5f85\u4ea4\u7ed9\u5171\u7528\u4ea4\u6613\u5904\u7406\u5668\u7684\u547d\u4ee4
     * @return \u547d\u4ee4\u6267\u884c\u7ed3\u679c
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result<?> execute(Command command) {
        return delegate.execute(command);
    }
}
