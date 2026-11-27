package io.ddd4j.sample.javalin.shiro.goods.web.dto;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 验证 <code>CreateGoodsRequest</code> 在<code>supportsBeanAndComponentAccessors</code>、<code>remainsSubclassable</code>等操作中的行为契约。
 */
class CreateGoodsRequestContractTest {

    /**
     * 验证<code>request.code()</code> 等于 <code>"SKU-1"</code>，<code>request.code()</code> 等于 <code>request.getCode()</code>，<code>request.toString()</code> 等于 <code>"CreateGoodsRequest[code=SKU-1, name=Coffee, price=12.50, stock=3]"</code>。
     */
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

    /**
     * 验证<code>subclass</code> 等于 <code>base</code>，<code>base</code> 等于 <code>subclass</code>，<code>subclass.hashCode()</code> 等于 <code>base.hashCode()</code>。
     */
    @Test
    void remainsSubclassableWithSymmetricValueEquality() {
        CreateGoodsRequest base = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        CreateGoodsRequest subclass = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3) {
        };

        assertEquals(base, subclass);
        assertEquals(subclass, base);
        assertEquals(base.hashCode(), subclass.hashCode());
    }

    /**
     * 验证<code>JsonKit.toObject(json, CreateGoodsRequest.class)</code> 等于 <code>original</code>。
     */
    @Test
    void roundTripsCurrentLineJsonMapper() {
        CreateGoodsRequest original = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        String json = JsonKit.toJson(original);
        assertEquals(original, JsonKit.toObject(json, CreateGoodsRequest.class));
    }

    /**
     * 验证<code>subclass</code> 不等于 <code>base</code>，<code>base</code> 不等于 <code>subclass</code>，<code>subclass.hashCode()</code> 不等于 <code>base.hashCode()</code>。
     */
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
