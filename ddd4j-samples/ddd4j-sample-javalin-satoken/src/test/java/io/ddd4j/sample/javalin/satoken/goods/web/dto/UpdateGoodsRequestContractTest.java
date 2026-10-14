package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UpdateGoodsRequestContractTest {

    @Test
    void supportsBeanAndComponentAccessorsWithStableValueBehavior() {
        assertFalse(Modifier.isFinal(UpdateGoodsRequest.class.getModifiers()));
        UpdateGoodsRequest request = new UpdateGoodsRequest();
        request.setName("Coffee");
        request.setPrice(new BigDecimal("12.50"));

        assertEquals("Coffee", request.name());
        assertEquals(request.getName(), request.name());
        assertEquals(new BigDecimal("12.50"), request.price());
        assertEquals(request.getPrice(), request.price());

        UpdateGoodsRequest same = new UpdateGoodsRequest("Coffee", new BigDecimal("12.50"));
        assertEquals(same, request);
        UpdateGoodsRequest subtype = new UpdateGoodsRequest("Coffee", new BigDecimal("12.50")) { };
        assertEquals(request, subtype);
        assertEquals(subtype, request);
        assertEquals(-854612899, request.hashCode());
        assertEquals("UpdateGoodsRequest[name=Coffee, price=12.50]", request.toString());
    }

    @Test
    void usesOverriddenBeanAccessorForValueComparison() {
        UpdateGoodsRequest base = new UpdateGoodsRequest("Coffee", new BigDecimal("12.50"));
        UpdateGoodsRequest changed = new UpdateGoodsRequest("Coffee", new BigDecimal("12.50")) {
            @Override
            public String getName() {
                return "Different";
            }
        };
        assertNotEquals(base, changed);
        assertNotEquals(changed, base);
        assertNotEquals(base.hashCode(), changed.hashCode());
    }
}
