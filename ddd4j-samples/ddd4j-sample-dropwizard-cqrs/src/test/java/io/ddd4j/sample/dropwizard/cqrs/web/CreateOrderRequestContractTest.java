package io.ddd4j.sample.dropwizard.cqrs.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import io.dropwizard.jackson.Jackson;
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

    @Test
    void requestReadsAndWritesTheOrderFields() throws Exception {
        ObjectMapper mapper = Jackson.newObjectMapper();
        String json = "{\"buyerName\":\"Buyer\",\"orderNo\":\"order-1\",\"buyerId\":\"buyer-1\"}";
        OrderResource.CreateOrderRequest request = new OrderResource.CreateOrderRequest("order-1", "buyer-1", "Buyer");

        assertEquals(request, mapper.readValue(json, OrderResource.CreateOrderRequest.class));
        assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsString(request)));
    }
}
