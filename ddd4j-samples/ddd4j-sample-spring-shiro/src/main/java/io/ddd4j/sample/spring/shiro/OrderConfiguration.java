package io.ddd4j.sample.spring.shiro;

import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 明确的单进程演示接线；复用共享订单业务，重启不保留数据。 */
@Configuration(proxyBeanMethods = false)
@Profile("demo-memory")
public class OrderConfiguration {
    /**
     * @return 当前应用独立的演示存储
     */
    @Bean
    public InMemoryOrderAdapters orderAdapters() {
        return new InMemoryOrderAdapters();
    }

    /**
     * @param adapters 共享端口实现
     * @return 不依赖命令总线的订单服务
     */
    @Bean
    public OrderApplicationService orderApplicationService(InMemoryOrderAdapters adapters) {
        return new OrderApplicationService(adapters, adapters, adapters, adapters, adapters);
    }
}
