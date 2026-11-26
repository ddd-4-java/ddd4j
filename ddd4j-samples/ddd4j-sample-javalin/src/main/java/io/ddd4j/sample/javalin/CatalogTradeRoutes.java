package io.ddd4j.sample.javalin;

import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.javalin.http.Context;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

final class CatalogTradeRoutes {
    private CatalogTradeRoutes() {
    }

    static void register(CatalogEndpoints api) {
        post("/api/goods/{code}", ctx -> respond(ctx, api.createGoods(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body())));
        get("/api/goods/{code}", ctx -> respond(ctx, api.goods(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"))));
        post("/api/orders/{code}", ctx -> respond(ctx, api.placeOrder(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body())));
        post("/api/orders/{orderNo}/payment", ctx -> respond(ctx, api.confirmPayment(
                ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body())));
        post("/api/orders/{orderNo}/cancel", ctx -> respond(ctx, api.cancelOrder(
                ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"))));
        post("/api/orders/{orderNo}/fulfill", ctx -> respond(ctx, api.fulfillOrder(
                ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"))));
        get("/api/orders/{code}", ctx -> respond(ctx, api.order(
                ctx.header("X-Tenant-Id"), ctx.pathParam("code"))));
    }

    private static void respond(Context ctx, Reply result) {
        ctx.status(result.getStatus()).contentType("application/json").result(result.getBody());
    }
}
