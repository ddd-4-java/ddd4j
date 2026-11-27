package io.ddd4j.sample.vertx.cqrs;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.vertx.VertxDomainEventPublisher;
import io.ddd4j.vertx.command.VertxCommandBus;
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
    /**
     * 限制调用方直接创建 <code>VertxCqrsApplication</code> 实例。
     */
    private VertxCqrsApplication() {
    }

    /**
     * 从系统属性读取端口，启动 Vert.x CQRS 示例并注册关闭钩子。
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        CatalogServer server = startCatalog(Integer.getInteger("sample.port", 8085), CatalogRuntime::start);
        log.info("Vert.x CQRS catalog listening {}", server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "vertx-catalog-close"));
    }

    /**
     * 启动启用了命令路由和事件发布的商品交易 CQRS 应用。
     *
     * @param port       当前 HTTP 示例要监听的端口
     * @param initialize 按框架发布器创建商品交易操作的工厂
     * @return 已启动的 HTTP 示例服务
     */
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

    /**
     * 把商品交易任务交给当前 Vert.x 请求的工作执行路径。
     *
     * @param context 当前 Vert.x 路由上下文
     * @param work    要在工作线程执行的商品交易操作
     */
    private static void catalogRequest(RoutingContext context, Callable<Reply> work) {
        context.vertx().executeBlocking(work).onSuccess(reply -> context.response()
                        .setStatusCode(reply.getStatus()).putHeader("Content-Type", "application/json").end(reply.getBody()))
                .onFailure(context::fail);
    }

    /**
     * 持有启动后的 Vert.x 商品交易 HTTP 服务及其关闭入口。
     */
    static final class CatalogServer implements AutoCloseable {
        /**
         * 为当前示例提供 HTTP 服务及工作线程的 Vert.x 实例。
         */
        private final Vertx vertx;
        /**
         * 由当前应用持有并在停止时关闭的 Vert.x HTTP 服务器。
         */
        private final HttpServer server;

        /**
         * 保存已启动的 Vert.x 服务和用于关闭其运行环境的实例。
         *
         * @param vertx  拥有当前 HTTP 服务器的 Vert.x 实例
         * @param server 当前示例启动的 Vert.x HTTP 服务
         */
        private CatalogServer(Vertx vertx, HttpServer server) {
            this.vertx = vertx;
            this.server = server;
        }

        /**
         * 读取 Vert.x 服务器实际绑定的端口。
         *
         * @return 当前可访问的 HTTP 端口
         */
        int port() {
            return server.actualPort();
        }

        /**
         * 读取<code>vertx</code> 字段。
         *
         * @return <code>vertx</code> 字段
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
