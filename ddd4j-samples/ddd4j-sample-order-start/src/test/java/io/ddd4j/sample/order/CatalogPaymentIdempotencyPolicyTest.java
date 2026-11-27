package io.ddd4j.sample.order;

import io.ddd4j.sample.order.start.CatalogPaymentIdempotencyPolicy;
import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 核对由订单快照处理重复支付的路由边界。 */
class CatalogPaymentIdempotencyPolicyTest {
    /** 支付确认可重放，其他写操作和其他 HTTP 方法仍由通用租约处理。 */
    @Test
    void onlyCatalogPaymentPostsUseOrderSnapshots() {
        CatalogPaymentIdempotencyPolicy policy = new CatalogPaymentIdempotencyPolicy();
        assertTrue(policy.handles(request("POST", "/api/orders/S1/payment")));
        assertTrue(policy.handles(request("POST", "/store/orders/catalog/orders/S1/payment")));
        assertFalse(policy.handles(request("GET", "/api/orders/S1/payment")));
        assertFalse(policy.handles(request("POST", "/api/orders/S1/cancel")));
        assertFalse(policy.handles(request("POST", "/admin/orders/S1/payment")));
        assertFalse(policy.handles(request("POST", "/api/orders/S1/items/I1/payment")));
    }

    /**
     * 创建带方法和路径的请求上下文。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @return 支付路由判定使用的上下文
     */
    private WebRequestContext request(String method, String path) {
        return new WebRequestContext("request", "trace", "tenant", null, Locale.ROOT,
                "127.0.0.1", method, path);
    }
}
