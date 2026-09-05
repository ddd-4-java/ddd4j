package io.ddd4j.sample.micronaut;

import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut 本地示例的订单端口装配。
 *
 * <p>此处只替换基础设施端口；领域与应用层均来自共享 Order 模块。
 */
@Factory
public class MicronautOrderFactory {

    @Singleton
    InMemoryOrderAdapters orderAdapters() {
        return new InMemoryOrderAdapters();
    }

    @Singleton
    OrderApplicationService orderApplicationService(InMemoryOrderAdapters adapters) {
        return new OrderApplicationService(adapters, adapters, adapters, adapters, adapters);
    }
}
