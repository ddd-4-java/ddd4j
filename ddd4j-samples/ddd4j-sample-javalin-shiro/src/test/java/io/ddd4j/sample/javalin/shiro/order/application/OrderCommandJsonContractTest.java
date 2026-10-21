package io.ddd4j.sample.javalin.shiro.order.application;

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

    @Test
    void createOrderCommandRetainsThreeLineValueHash() {
        CreateOrderCommand command = new CreateOrderCommand("O-1", null, "Alice");
        int expected = 1;
        expected = 59 * expected + "O-1".hashCode();
        expected = 59 * expected + 43;
        expected = 59 * expected + "Alice".hashCode();

        assertEquals(expected, command.hashCode());
    }

    @Test
    void addOrderLineCommandRetainsThreeLineValueHash() {
        AddOrderLineCommand command = new AddOrderLineCommand(
                "order-1", null, "Coffee", 2, new BigDecimal("12.50"));
        int expected = 1;
        expected = 59 * expected + 2;
        expected = 59 * expected + "order-1".hashCode();
        expected = 59 * expected + 43;
        expected = 59 * expected + "Coffee".hashCode();
        expected = 59 * expected + new BigDecimal("12.50").hashCode();

        assertEquals(expected, command.hashCode());
    }
}
