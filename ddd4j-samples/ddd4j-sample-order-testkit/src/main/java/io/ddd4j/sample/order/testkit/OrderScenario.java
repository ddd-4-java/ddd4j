package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;

import java.math.BigDecimal;

public final class OrderScenario {

    private final InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
    private final OrderApplicationService application = new OrderApplicationService(
            adapters, adapters, adapters, adapters, adapters);

    public Order paidOrder(String orderNo) {
        Order order = application.create(new CreateOrderCommand(orderNo, "buyer-1", "Alice"));
        application.addLine(new AddOrderLineCommand(order.id(), "goods-1", "DDD Book", 2,
                new BigDecimal("59.90")));
        return application.pay(order.id(), "payment-" + orderNo);
    }

    public OrderApplicationService application() {
        return application;
    }

    public InMemoryOrderAdapters adapters() {
        return adapters;
    }
}
