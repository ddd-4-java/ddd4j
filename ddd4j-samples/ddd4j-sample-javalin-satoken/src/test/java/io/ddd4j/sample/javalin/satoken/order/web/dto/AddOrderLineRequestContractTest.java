package io.ddd4j.sample.javalin.satoken.order.web.dto;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AddOrderLineRequestContractTest {

    @Test
    void supportsBeanAndComponentAccessorsWithStableValueBehavior() {
        AddOrderLineRequest request = new AddOrderLineRequest();
        request.setGoodsId("G-1");
        request.setGoodsName("Coffee");
        request.setQuantity(2);
        request.setUnitPrice(new BigDecimal("12.50"));

        assertEquals("G-1", request.goodsId());
        assertEquals(request.getGoodsId(), request.goodsId());
        assertEquals("Coffee", request.goodsName());
        assertEquals(request.getGoodsName(), request.goodsName());
        assertEquals(2, request.quantity());
        assertEquals(request.getQuantity(), request.quantity());
        assertEquals(new BigDecimal("12.50"), request.unitPrice());
        assertEquals(request.getUnitPrice(), request.unitPrice());

        AddOrderLineRequest same = new AddOrderLineRequest("G-1", "Coffee", 2, new BigDecimal("12.50"));
        assertEquals(same, request);
        assertEquals(-599549586, request.hashCode());
        assertEquals("AddOrderLineRequest[goodsId=G-1, goodsName=Coffee, quantity=2, unitPrice=12.50]",
                request.toString());
    }

    @Test
    void remainsSubclassableWithSymmetricValueEquality() {
        AddOrderLineRequest base = new AddOrderLineRequest("G-1", "Coffee", 2, new BigDecimal("12.50"));
        AddOrderLineRequest subclass = new AddOrderLineRequest("G-1", "Coffee", 2, new BigDecimal("12.50")) {
        };

        assertEquals(base, subclass);
        assertEquals(subclass, base);
        assertEquals(base.hashCode(), subclass.hashCode());
    }

    @Test
    void roundTripsCurrentLineJsonMapper() {
        AddOrderLineRequest original = new AddOrderLineRequest("G-1", "Coffee", 2, new BigDecimal("12.50"));
        String json = JsonKit.toJson(original);
        assertEquals(original, JsonKit.toObject(json, AddOrderLineRequest.class));
    }

    @Test
    void usesOverriddenBeanAccessorForValueComparison() {
        AddOrderLineRequest base = new AddOrderLineRequest("G-1", "Coffee", 2, new BigDecimal("12.50"));
        AddOrderLineRequest subclass = new AddOrderLineRequest("G-1", "Coffee", 2, new BigDecimal("12.50")) {
            @Override
            public String getGoodsId() {
                return "different";
            }
        };

        assertNotEquals(base, subclass);
        assertNotEquals(subclass, base);
        assertNotEquals(base.hashCode(), subclass.hashCode());
    }
}
