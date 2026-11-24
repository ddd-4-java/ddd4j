package io.ddd4j.sample.order.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.sample.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderExplicitJsonContractTest {

    @Test
    void createCommandRetainsAllFieldsWithBeanDiscoveryDisabled() throws Exception {
        assertRoundTrip(new CreateOrderCommand("O-1", "B-1", "Alice"), CreateOrderCommand.class,
                "orderNo", "buyerId", "buyerName");
    }

    @Test
    void addLineRetainsAllFieldsWithBeanDiscoveryDisabled() throws Exception {
        assertRoundTrip(new AddOrderLineCommand("id-1", "G-1", "Coffee", 2, new BigDecimal("12.50")),
                AddOrderLineCommand.class, "orderId", "goodsId", "goodsName", "quantity", "unitPrice");
    }

    @Test
    void readModelRetainsAllFieldsWithBeanDiscoveryDisabled() throws Exception {
        assertRoundTrip(new OrderReadModel("id-1", "O-1", "B-1", "Alice", OrderStatus.DRAFT,
                        new BigDecimal("25.00")),
                OrderReadModel.class, "id", "orderNo", "buyerId", "buyerName", "status", "totalAmount");
    }

    @Test
    void dispatchResultRetainsAllFieldsWithBeanDiscoveryDisabled() throws Exception {
        assertRoundTrip(new OutboxDispatchResult(3, 2, 1), OutboxDispatchResult.class,
                "attempted", "published", "failed");
    }

    @Test
    void outboxMessageRetainsAllFieldsWithBeanDiscoveryDisabled() throws Exception {
        assertRoundTrip(new OutboxMessage("msg-1", "id-1", "OrderCreated", "{}", null), OutboxMessage.class,
                "id", "aggregateId", "eventType", "payload", "occurredAt");
    }

    private static <T> void assertRoundTrip(T original, Class<T> type, String... properties) throws Exception {
        JsonMapper mapper = JsonMapper.builder().addMixIn(type, ExplicitProperties.class).build();
        String json = mapper.writeValueAsString(original);
        JsonNode node = mapper.readTree(json);
        assertEquals(properties.length, node.size());
        for (String property : properties) {
            assertTrue(node.has(property), () -> "Missing " + property + " in " + json);
        }
        assertEquals(original, mapper.readValue(json, type));
    }

    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
    private abstract static class ExplicitProperties {
    }
}
