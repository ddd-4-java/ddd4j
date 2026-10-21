package io.ddd4j.sample.javalin.shiro.order.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderResponseJsonContractTest {

    @Test
    void responseAndNestedLineRoundTripThroughHttpJson() throws Exception {
        OrderResponse.OrderLineResponse line = new OrderResponse.OrderLineResponse(
                "L-1", "G-1", "Coffee", 2, "12.50", "CNY");
        OrderResponse original = new OrderResponse(
                "id-1", "O-1", "B-1", "Alice", OrderStatus.DRAFT,
                "25.00", "CNY", Arrays.asList(line));

        ObjectMapper mapper = new ObjectMapper();
        OrderResponse restored = mapper.readValue(mapper.writeValueAsString(original), OrderResponse.class);

        assertEquals(original, restored);
        assertEquals(line, restored.lines().get(0));
    }
}
