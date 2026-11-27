package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 将共用商品交易命令处理器注册到示例运行框架的命令总线。
 */
@Component
public class CatalogCommandExecutor implements CommandExecutor<Command> {
    /**
     * 与具体框架无关的商品交易 HTTP 操作。
     */
    private final CatalogApi api;
    /**
     * 执行共用商品交易命令的处理器。
     */
    private final CommandExecutor<Command> delegate;

    /**
     * 使用运行框架提供的数据库配置和领域事件发布器初始化交易命令执行器。
     *
     * @param environment 提供数据库连接配置的 Spring 环境
     * @param publisher   负责将交易领域事件转发给运行框架的发布器
     */
    public CatalogCommandExecutor(Environment environment, ApplicationEventPublisher publisher) {
        api = CatalogRuntime.start(environment.getRequiredProperty("CATALOG_JDBC_URL"),
                environment.getRequiredProperty("CATALOG_DB_USER"), environment.getRequiredProperty("CATALOG_DB_PASSWORD"),
                new SpringDomainEventPublisher(publisher));
        delegate = CatalogCqrs.executor(api);
    }

    /**
     * 返回已完成装配的共用交易入口，供 HTTP 适配器读取商品及订单。
     *
     * @return 商品交易 HTTP 操作
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
     * 将商品交易命令交给共用的命令执行器。
     *
     * @param command 待交给共用交易处理器的命令
     * @return 命令执行结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public R<?> execute(Command command) {
        return delegate.execute(command);
    }
}
