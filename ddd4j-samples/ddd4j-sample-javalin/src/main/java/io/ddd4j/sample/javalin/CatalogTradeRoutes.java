package io.ddd4j.sample.javalin;

import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.javalin.http.Context;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * 将商品创建、下单和履约等操作注册为 Javalin HTTP 路由。
 */
final class CatalogTradeRoutes {
    /**
     * 限制调用方直接创建 <code>CatalogTradeRoutes</code> 实例。
     */
    private CatalogTradeRoutes() {
    }

    /**
     * 将共用商品交易用例接入当前 Javalin 路由。
     *
     * @param api 共用的商品交易 HTTP 操作
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
     * 将应用层返回的状态和正文写入 Javalin 响应。
     *
     * @param ctx    当前 Web 框架的请求上下文
     * @param result 商品交易用例返回的 HTTP 状态和正文
     */
    private static void respond(Context ctx, Reply result) {
        ctx.status(result.getStatus()).contentType("application/json").result(result.getBody());
    }
}
