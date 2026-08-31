package io.ddd4j.ddd.config;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** JDK8 原生 DDD/CQRS 自动配置。 */
@Configuration
public class DddAutoConfiguration {
    /** 开发和测试使用的本地内存事件存储；生产可覆盖此 Bean。 */
    @Bean
    public EventStore inMemoryEventStore() { return new InMemoryEventStore(); }

    /** 收集原生命令执行器，并使用统一的重复执行器 fail-fast 路由规则。 */
    @Bean
    public CommandBus dddCommandBus(ObjectProvider<CommandExecutor> executorsProvider) {
        List<CommandExecutor<?>> executors = new ArrayList<CommandExecutor<?>>();
        for (CommandExecutor executor : executorsProvider) { executors.add(executor); }
        return new DefaultCommandBus(executors);
    }
}
