package io.ddd4j.sample.quarkus.satoken.order.web;

import io.ddd4j.sample.quarkus.satoken.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.satoken.order.domain.service.OrderDomainService;
import io.ddd4j.sample.quarkus.satoken.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.quarkus.satoken.order.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 订单列表返回值的三线行为契约。 */
class OrderListContractTest {

    @Test
    void listResultRemainsMutable() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderApplicationService service = new OrderApplicationService(repository, new OrderDomainService(repository));
        List<OrderResponse> data = new OrderResource(service).listAll().getData();
        assertEquals(0, data.size());
        data.add(null);
        assertEquals(1, data.size());
    }
}
