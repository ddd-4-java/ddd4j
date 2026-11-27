package io.ddd4j.sample.javalin;

import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.javalin.http.Context;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/** \u5c06\u5546\u54c1\u521b\u5efa\u3001\u4e0b\u5355\u548c\u5c65\u7ea6\u7b49\u64cd\u4f5c\u6ce8\u518c\u4e3a Javalin HTTP \u8def\u7531\u3002 */
final class CatalogTradeRoutes {
    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>CatalogTradeRoutes</code> \u5b9e\u4f8b\u3002 */
    private CatalogTradeRoutes() {
    }

    /**
     * \u5c06\u5171\u7528\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u63a5\u5165\u5f53\u524d Javalin \u8def\u7531\u3002
     * @param api \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    static void register(CatalogEndpoints api) {
        post("/api/goods/{code}", ctx -> respond(ctx, api.createGoods(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body())));
        get("/api/goods/{code}", ctx -> respond(ctx, api.goods(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"))));
        post("/api/orders/{code}", ctx -> respond(ctx, api.placeOrder(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body())));
        post("/api/orders/{orderNo}/payment", ctx -> respond(ctx, api.confirmPayment(ctx.header("Idempotency-Key"),
                ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body())));
        post("/api/orders/{orderNo}/cancel", ctx -> respond(ctx, api.cancelOrder(
                ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"))));
        post("/api/orders/{orderNo}/fulfill", ctx -> respond(ctx, api.fulfillOrder(
                ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"))));
        get("/api/orders/{code}", ctx -> respond(ctx, api.order(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"))));
    }

    /**
     * \u5c06\u5e94\u7528\u5c42\u8fd4\u56de\u7684\u72b6\u6001\u548c\u6b63\u6587\u5199\u5165 Javalin \u54cd\u5e94\u3002
     * @param ctx \u5f53\u524d Web \u6846\u67b6\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param result \u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u8fd4\u56de\u7684 HTTP \u72b6\u6001\u548c\u6b63\u6587
     */
    private static void respond(Context ctx, Reply result) {
        ctx.status(result.getStatus()).contentType("application/json").result(result.getBody());
    }
}
