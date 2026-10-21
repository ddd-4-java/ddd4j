package io.ddd4j.sample.dropwizard.cqrs.web;

import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderResourceMapContractTest {

    @Test
    void createdAndDuplicateResponsesExposeImmutableMaps() {
        OrderResource resource = new OrderResource();
        String orderNo = "order-" + UUID.randomUUID();
        OrderResource.CreateOrderRequest request =
                new OrderResource.CreateOrderRequest(orderNo, "buyer-1", "Alice");

        Response created = resource.createOrder(request);
        assertEquals(201, created.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> createdBody = (Map<String, Object>) created.getEntity();
        assertEquals(true, createdBody.get("success"));
        assertThrows(UnsupportedOperationException.class, () -> createdBody.put("extra", "value"));

        Response duplicate = resource.createOrder(request);
        assertEquals(409, duplicate.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> duplicateBody = (Map<String, Object>) duplicate.getEntity();
        assertEquals(false, duplicateBody.get("success"));
        assertThrows(UnsupportedOperationException.class, () -> duplicateBody.put("extra", "value"));
    }
}
