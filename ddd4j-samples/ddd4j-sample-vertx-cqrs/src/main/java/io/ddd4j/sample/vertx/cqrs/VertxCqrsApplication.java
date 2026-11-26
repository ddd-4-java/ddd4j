package io.ddd4j.sample.vertx.cqrs;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.vertx.VertxDomainEventPublisher;
import io.ddd4j.data.cqrs.vertx.VertxCommandBus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Vert.x 四表交易示例，使用原生命令总线、事件总线和可重建订单视图。
 */
@Slf4j
public final class VertxCqrsApplication {
    private VertxCqrsApplication() {
    }

    public static void main(String[] args) {
        CatalogServer server = startCatalog(Integer.getInteger("sample.port", 8085), CatalogRuntime::start);
        log.info("Vert.x CQRS catalog listening {}", server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "vertx-catalog-close"));
    }

    static CatalogServer startCatalog(int port, Function<DomainEventPublisher, CatalogApi> initialize) {
        Vertx vertx = Vertx.vertx();
        try {
            CatalogApi api = initialize.apply(new VertxDomainEventPublisher(vertx));
            CatalogCqrs catalog = new CatalogCqrs(api, VertxCommandBus.create(vertx, List.of(CatalogCqrs.executor(api))));
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());
            router.post("/orders/catalog/goods/:code").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.createGoods(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"), ctx.body().asString())));
            router.get("/orders/catalog/goods/:code").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.goods(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
            router.post("/orders/catalog/orders/:code").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.placeOrder(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"), ctx.body().asString())));
            router.post("/orders/catalog/orders/:code/payment").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.confirmPayment(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"), ctx.body().asString())));
            router.post("/orders/catalog/orders/:code/cancel").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.cancelOrder(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
            router.post("/orders/catalog/orders/:code/fulfill").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.fulfillOrder(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
            router.get("/orders/catalog/orders/:code").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.order(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
            router.post("/orders/catalog/projections/orders/:code").handler(ctx -> catalogRequest(ctx, () ->
                    catalog.project(ctx.request().getHeader("X-Tenant-Id"), ctx.pathParam("code"))));
            var server = vertx.createHttpServer().requestHandler(router).listen(port, "127.0.0.1")
                    .toCompletionStage().toCompletableFuture().join();
            return new CatalogServer(vertx, server);
        } catch (RuntimeException | Error failure) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
            throw failure;
        }
    }

    private static void catalogRequest(RoutingContext context, Callable<Reply> work) {
        context.vertx().executeBlocking(work).onSuccess(reply -> context.response()
                        .setStatusCode(reply.getStatus()).putHeader("Content-Type", "application/json").end(reply.getBody()))
                .onFailure(context::fail);
    }

    static final class CatalogServer implements AutoCloseable {
        private final Vertx vertx;
        private final HttpServer server;

        private CatalogServer(Vertx vertx, HttpServer server) {
            this.vertx = vertx;
            this.server = server;
        }

        int port() {
            return server.actualPort();
        }

        Vertx vertx() {
            return vertx;
        }

        @Override
        public void close() {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }
}
