package io.ddd4j.sample.vertx.cqrs;

import com.google.common.collect.ImmutableList;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.vertx.VertxDomainEventPublisher;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
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
    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>VertxCqrsApplication</code> \u5b9e\u4f8b\u3002 */
    private VertxCqrsApplication() {
    }

    /**
     * \u4ece\u7cfb\u7edf\u5c5e\u6027\u8bfb\u53d6\u7aef\u53e3\uff0c\u542f\u52a8 Vert.x CQRS \u793a\u4f8b\u5e76\u6ce8\u518c\u5173\u95ed\u94a9\u5b50\u3002
     * @param args \u5e94\u7528\u542f\u52a8\u53c2\u6570
     */
    public static void main(String[] args) {
        CatalogServer server = startCatalog(Integer.getInteger("sample.port", 8085), CatalogRuntime::start);
        log.info("Vert.x CQRS catalog listening {}", server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "vertx-catalog-close"));
    }

    /**
     * \u542f\u52a8\u542f\u7528\u4e86\u547d\u4ee4\u8def\u7531\u548c\u4e8b\u4ef6\u53d1\u5e03\u7684\u5546\u54c1\u4ea4\u6613 CQRS \u5e94\u7528\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param initialize \u6309\u6846\u67b6\u53d1\u5e03\u5668\u521b\u5efa\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7684\u5de5\u5382
     * @return \u5df2\u542f\u52a8\u7684 HTTP \u793a\u4f8b\u670d\u52a1
     */
    static CatalogServer startCatalog(int port, Function<DomainEventPublisher, CatalogApi> initialize) {
        Vertx vertx = Vertx.vertx();
        try {
            CatalogApi api = initialize.apply(new VertxDomainEventPublisher(vertx));
            CatalogCqrs catalog = new CatalogCqrs(api, new DefaultCommandBus(ImmutableList.of(CatalogCqrs.executor(api))));
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
            HttpServer server = vertx.createHttpServer().requestHandler(router).listen(port, "127.0.0.1")
                    .toCompletionStage().toCompletableFuture().join();
            return new CatalogServer(vertx, server);
        } catch (RuntimeException | Error failure) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
            throw failure;
        }
    }

    /**
     * \u628a\u5546\u54c1\u4ea4\u6613\u4efb\u52a1\u4ea4\u7ed9\u5f53\u524d Vert.x \u8bf7\u6c42\u7684\u5de5\u4f5c\u6267\u884c\u8def\u5f84\u3002
     * @param context \u5f53\u524d Vert.x \u8def\u7531\u4e0a\u4e0b\u6587
     * @param work \u8981\u5728\u5de5\u4f5c\u7ebf\u7a0b\u6267\u884c\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c
     */
    private static void catalogRequest(RoutingContext context, Callable<Reply> work) {
        context.vertx().executeBlocking(work).onSuccess(reply -> context.response()
                        .setStatusCode(reply.getStatus()).putHeader("Content-Type", "application/json").end(reply.getBody()))
                .onFailure(context::fail);
    }

    /** \u6301\u6709\u542f\u52a8\u540e\u7684 Vert.x \u5546\u54c1\u4ea4\u6613 HTTP \u670d\u52a1\u53ca\u5176\u5173\u95ed\u5165\u53e3\u3002 */
    static final class CatalogServer implements AutoCloseable {
        /** \u4e3a\u5f53\u524d\u793a\u4f8b\u63d0\u4f9b HTTP \u670d\u52a1\u53ca\u5de5\u4f5c\u7ebf\u7a0b\u7684 Vert.x \u5b9e\u4f8b\u3002 */
        private final Vertx vertx;
        /** \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u505c\u6b62\u65f6\u5173\u95ed\u7684 Vert.x HTTP \u670d\u52a1\u5668\u3002 */
        private final HttpServer server;

        /**
         * \u4fdd\u5b58\u5df2\u542f\u52a8\u7684 Vert.x \u670d\u52a1\u548c\u7528\u4e8e\u5173\u95ed\u5176\u8fd0\u884c\u73af\u5883\u7684\u5b9e\u4f8b\u3002
         * @param vertx \u62e5\u6709\u5f53\u524d HTTP \u670d\u52a1\u5668\u7684 Vert.x \u5b9e\u4f8b
         * @param server \u5f53\u524d\u793a\u4f8b\u542f\u52a8\u7684 Vert.x HTTP \u670d\u52a1
         */
        private CatalogServer(Vertx vertx, HttpServer server) {
            this.vertx = vertx;
            this.server = server;
        }

        /**
         * \u8bfb\u53d6 Vert.x \u670d\u52a1\u5668\u5b9e\u9645\u7ed1\u5b9a\u7684\u7aef\u53e3\u3002
         * @return \u5f53\u524d\u53ef\u8bbf\u95ee\u7684 HTTP \u7aef\u53e3
         */
        int port() {
            return server.actualPort();
        }

        /**
         * \u8bfb\u53d6<code>vertx</code> \u5b57\u6bb5\u3002
         * @return <code>vertx</code> \u5b57\u6bb5
         */
        Vertx vertx() {
            return vertx;
        }

        /**
         * 结束当前组件持有的资源。
         *
         * <p>此实现调用 <code>vertx.close().toCompletionStage().toCompletableFuture().join()</code>。</p>
         */
        @Override
        public void close() {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }
}
