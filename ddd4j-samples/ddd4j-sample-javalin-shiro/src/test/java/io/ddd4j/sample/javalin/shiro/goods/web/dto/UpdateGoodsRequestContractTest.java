package io.ddd4j.sample.javalin.shiro.goods.web.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>UpdateGoodsRequest</code> \u5728<code>supportsBeanAndComponentAccessors</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class UpdateGoodsRequestContractTest {

    /** \u9a8c\u8bc1<code>Modifier.isFinal(UpdateGoodsRequest.class.getModifiers())</code>\u4e3a\u5047\uff0c<code>request.name()</code> \u7b49\u4e8e <code>"Coffee"</code>\uff0c<code>request.toString()</code> \u7b49\u4e8e <code>"UpdateGoodsRequest[name=Coffee, price=12.50]"</code>\u3002 */
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
        UpdateGoodsRequest subtype = new UpdateGoodsRequest("Coffee", new BigDecimal("12.50")) {
        };
        assertEquals(request, subtype);
        assertEquals(subtype, request);
        assertEquals(-854612899, request.hashCode());
        assertEquals("UpdateGoodsRequest[name=Coffee, price=12.50]", request.toString());
    }

    /** \u9a8c\u8bc1<code>changed</code> \u4e0d\u7b49\u4e8e <code>base</code>\uff0c<code>base</code> \u4e0d\u7b49\u4e8e <code>changed</code>\uff0c<code>changed.hashCode()</code> \u4e0d\u7b49\u4e8e <code>base.hashCode()</code>\u3002 */
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
