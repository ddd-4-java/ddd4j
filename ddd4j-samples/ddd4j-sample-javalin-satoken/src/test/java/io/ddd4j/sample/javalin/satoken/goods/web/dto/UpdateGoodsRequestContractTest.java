package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>UpdateGoodsRequest</code> 在<code>supportsBeanAndComponentAccessors</code>等操作中的行为契约。
 */
class UpdateGoodsRequestContractTest {

    /**
     * 验证<code>Modifier.isFinal(UpdateGoodsRequest.class.getModifiers())</code>为假，<code>request.name()</code> 等于 <code>"Coffee"</code>，<code>request.toString()</code> 等于 <code>"UpdateGoodsRequest[name=Coffee, price=12.50]"</code>。
     */
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

    /**
     * 验证<code>changed</code> 不等于 <code>base</code>，<code>base</code> 不等于 <code>changed</code>，<code>changed.hashCode()</code> 不等于 <code>base.hashCode()</code>。
     */
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
