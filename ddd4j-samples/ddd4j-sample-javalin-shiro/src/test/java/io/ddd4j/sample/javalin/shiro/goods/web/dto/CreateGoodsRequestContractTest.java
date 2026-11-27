package io.ddd4j.sample.javalin.shiro.goods.web.dto;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** \u9a8c\u8bc1 <code>CreateGoodsRequest</code> \u5728<code>supportsBeanAndComponentAccessors</code>\u3001<code>remainsSubclassable</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class CreateGoodsRequestContractTest {

    /** \u9a8c\u8bc1<code>request.code()</code> \u7b49\u4e8e <code>"SKU-1"</code>\uff0c<code>request.code()</code> \u7b49\u4e8e <code>request.getCode()</code>\uff0c<code>request.toString()</code> \u7b49\u4e8e <code>"CreateGoodsRequest[code=SKU-1, name=Coffee, price=12.50, stock=3]"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>subclass</code> \u7b49\u4e8e <code>base</code>\uff0c<code>base</code> \u7b49\u4e8e <code>subclass</code>\uff0c<code>subclass.hashCode()</code> \u7b49\u4e8e <code>base.hashCode()</code>\u3002 */
    @Test
    void remainsSubclassableWithSymmetricValueEquality() {
        CreateGoodsRequest base = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        CreateGoodsRequest subclass = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3) {
        };

        assertEquals(base, subclass);
        assertEquals(subclass, base);
        assertEquals(base.hashCode(), subclass.hashCode());
    }

    /** \u9a8c\u8bc1<code>JsonKit.toObject(json, CreateGoodsRequest.class)</code> \u7b49\u4e8e <code>original</code>\u3002 */
    @Test
    void roundTripsCurrentLineJsonMapper() {
        CreateGoodsRequest original = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        String json = JsonKit.toJson(original);
        assertEquals(original, JsonKit.toObject(json, CreateGoodsRequest.class));
    }

    /** \u9a8c\u8bc1<code>subclass</code> \u4e0d\u7b49\u4e8e <code>base</code>\uff0c<code>base</code> \u4e0d\u7b49\u4e8e <code>subclass</code>\uff0c<code>subclass.hashCode()</code> \u4e0d\u7b49\u4e8e <code>base.hashCode()</code>\u3002 */
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
