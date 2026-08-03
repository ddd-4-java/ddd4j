package io.ddd4j.sample.helidon;

import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

/**
 * Helidon CDI 中共享 Order 端口的本地示例装配。
 */
@ApplicationScoped
public class HelidonOrderBeans {

    @Produces
    @Dependent
    InMemoryOrderAdapters orderAdapters() {
        return new InMemoryOrderAdapters();
    }

    @Produces
    @ApplicationScoped
    OrderApplicationService orderApplicationService(InMemoryOrderAdapters adapters) {
        return new OrderApplicationService(adapters, adapters, adapters, adapters, adapters);
    }
}
