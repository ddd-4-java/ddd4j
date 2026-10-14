package io.ddd4j.sample.javalin.shiro.order.web.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CreateOrderRequestContractTest {

    @Test
    void supportsBeanAndComponentAccessorsWithStableValueBehavior() {
        assertFalse(Modifier.isFinal(CreateOrderRequest.class.getModifiers()));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderNo("ORDER-1");
        request.setBuyerId("buyer-1");
        request.setBuyerName("Alice");

        assertEquals("ORDER-1", request.orderNo());
        assertEquals(request.getOrderNo(), request.orderNo());
        assertEquals("buyer-1", request.buyerId());
        assertEquals(request.getBuyerId(), request.buyerId());
        assertEquals("Alice", request.buyerName());
        assertEquals(request.getBuyerName(), request.buyerName());

        CreateOrderRequest same = new CreateOrderRequest("ORDER-1", "buyer-1", "Alice");
        assertEquals(same, request);
        CreateOrderRequest subtype = new CreateOrderRequest("ORDER-1", "buyer-1", "Alice") { };
        assertEquals(request, subtype);
        assertEquals(subtype, request);
        assertEquals(-181542542, request.hashCode());
        assertEquals("CreateOrderRequest[orderNo=ORDER-1, buyerId=buyer-1, buyerName=Alice]", request.toString());
    }

    @Test
    void usesOverriddenBeanAccessorForValueComparison() {
        CreateOrderRequest base = new CreateOrderRequest("ORDER-1", "buyer-1", "Alice");
        CreateOrderRequest changed = new CreateOrderRequest("ORDER-1", "buyer-1", "Alice") {
            @Override
            public String getBuyerId() {
                return "different-buyer";
            }
        };
        assertNotEquals(base, changed);
        assertNotEquals(changed, base);
        assertNotEquals(base.hashCode(), changed.hashCode());
    }
}
