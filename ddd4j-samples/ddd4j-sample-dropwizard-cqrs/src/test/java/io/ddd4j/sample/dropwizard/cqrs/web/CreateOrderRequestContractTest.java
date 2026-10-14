package io.ddd4j.sample.dropwizard.cqrs.web;

import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateOrderRequestContractTest {

    @Test
    void requestSupportsMutableBeanAndComponentAccess() {
        OrderResource.CreateOrderRequest request = new OrderResource.CreateOrderRequest();
        request.setOrderNo("order-1");
        request.setBuyerId("buyer-1");
        request.setBuyerName("Buyer");

        assertEquals("order-1", request.orderNo());
        assertEquals(request.orderNo(), request.getOrderNo());
        assertEquals(request.buyerId(), request.getBuyerId());
        assertEquals(request.buyerName(), request.getBuyerName());
        assertEquals(new OrderResource.CreateOrderRequest("order-1", "buyer-1", "Buyer"), request);
    }

    @Test
    void commandRetainsComponentAndBeanAccess() {
        CreateOrderCommand command = new CreateOrderCommand("order-1", "buyer-1", "Buyer");
        assertEquals(command.orderNo(), command.getOrderNo());
        assertEquals(command.buyerId(), command.getBuyerId());
        assertEquals(command.buyerName(), command.getBuyerName());
    }
}
