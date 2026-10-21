package io.ddd4j.sample.javalin.satoken.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCommandJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createOrderCommandRoundTripsThroughJson() throws Exception {
        CreateOrderCommand original = new CreateOrderCommand("O-1", "B-1", "Alice");

        CreateOrderCommand restored = mapper.readValue(
                mapper.writeValueAsString(original), CreateOrderCommand.class);

        assertEquals(original, restored);
    }

    @Test
    void addOrderLineCommandRoundTripsThroughJson() throws Exception {
        AddOrderLineCommand original = new AddOrderLineCommand(
                "order-1", "goods-1", "Coffee", 2, new BigDecimal("12.50"));

        AddOrderLineCommand restored = mapper.readValue(
                mapper.writeValueAsString(original), AddOrderLineCommand.class);

        assertEquals(original, restored);
    }
}
