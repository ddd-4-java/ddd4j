package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CreateGoodsRequestContractTest {

    @Test
    void supportsBeanAndComponentAccessorsWithStableValueBehavior() {
        CreateGoodsRequest request = new CreateGoodsRequest();
        request.setCode("SKU-1");
        request.setName("Coffee");
        request.setPrice(new BigDecimal("12.50"));
        request.setStock(3);

        assertEquals("SKU-1", request.code());
        assertEquals(request.getCode(), request.code());
        assertEquals("Coffee", request.name());
        assertEquals(request.getName(), request.name());
        assertEquals(new BigDecimal("12.50"), request.price());
        assertEquals(request.getPrice(), request.price());
        assertEquals(Integer.valueOf(3), request.stock());
        assertEquals(request.getStock(), request.stock());

        CreateGoodsRequest same = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        assertEquals(same, request);
        assertEquals(-826274569, request.hashCode());
        assertEquals("CreateGoodsRequest[code=SKU-1, name=Coffee, price=12.50, stock=3]", request.toString());
    }

    @Test
    void remainsSubclassableWithSymmetricValueEquality() {
        CreateGoodsRequest base = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        CreateGoodsRequest subclass = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3) {
        };

        assertEquals(base, subclass);
        assertEquals(subclass, base);
        assertEquals(base.hashCode(), subclass.hashCode());
    }

    @Test
    void roundTripsCurrentLineJsonMapper() {
        CreateGoodsRequest original = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        String json = JsonKit.toJson(original);
        assertEquals(original, JsonKit.toObject(json, CreateGoodsRequest.class));
    }

    @Test
    void usesOverriddenBeanAccessorForValueComparison() {
        CreateGoodsRequest base = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        CreateGoodsRequest subclass = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3) {
            @Override
            public String getCode() {
                return "different";
            }
        };

        assertNotEquals(base, subclass);
        assertNotEquals(subclass, base);
        assertNotEquals(base.hashCode(), subclass.hashCode());
    }
}
