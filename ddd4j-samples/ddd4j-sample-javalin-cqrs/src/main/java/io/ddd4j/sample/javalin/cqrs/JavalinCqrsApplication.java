package io.ddd4j.sample.javalin.cqrs;

import com.google.common.eventbus.EventBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.guice.command.GuiceCommandBus;
import io.ddd4j.guice.event.GuiceDomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Javalin 四表商品交易，使用 Guice 命令总线和领域事件发布器。
 */
@Slf4j
public final class JavalinCqrsApplication {
    /**
     * 未指定监听端口时使用的默认端口。
     */
    public static final int DEFAULT_PORT = 7001;

    /**
     * 限制调用方直接创建 <code>JavalinCqrsApplication</code> 实例。
     */
    private JavalinCqrsApplication() {
    }

    /**
     * 按启动参数配置 HTTP 端口，启动 Javalin CQRS 示例并注册关闭钩子。
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        Javalin app = startCatalog(parsePort(args), CatalogRuntime::start, new EventBus());
        log.info("Javalin CQRS catalog listening {}", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "javalin-catalog-cqrs-close"));
    }

    /**
     * 启动启用了命令路由和事件发布的商品交易 CQRS 应用。
     *
     * @param port       当前 HTTP 示例要监听的端口
     * @param initialize 按框架发布器创建商品交易操作的工厂
     * @param events     转发当前应用领域事件的事件总线
     * @return 已启动的 HTTP 示例服务
     */
    static Javalin startCatalog(int port, Function<DomainEventPublisher, CatalogApi> initialize, EventBus events) {
        var api = initialize.apply(new GuiceDomainEventPublisher(events));
        var catalog = new CatalogCqrs(api, new GuiceCommandBus(List.of(CatalogCqrs.executor(api))));
        Javalin app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.apiBuilder(() -> catalogRoutes(catalog));
        });
        try {
            return app.start(port);
        } catch (RuntimeException | Error failure) {
            try {
                app.stop();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * 从命令行读取监听端口，缺省时使用示例默认端口。
     *
     * @param args 应用启动时提供的命令行参数
     * @return 当前应用启动时使用的端口
     */
    private static int parsePort(String[] args) {
        if (Objects.isNull(args) || args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            log.warn("[Bootstrap] invalid port arg, fallback to {}", DEFAULT_PORT, exception);
            return DEFAULT_PORT;
        }
    }

    /**
     * 注册接入 CQRS 命令和投影的商品交易路由。
     *
     * @param catalog 当前商品交易入口
     */
    private static void catalogRoutes(CatalogCqrs catalog) {
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/goods/{code}", ctx -> {
            var reply = catalog.createGoods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.get("/orders/catalog/goods/{code}", ctx -> {
            var reply = catalog.goods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{code}", ctx -> {
            var reply = catalog.placeOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{orderNo}/payment", ctx -> {
            var reply = catalog.confirmPayment(ctx.header("Idempotency-Key"), ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body());
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{orderNo}/cancel", ctx -> {
            var reply = catalog.cancelOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{orderNo}/fulfill", ctx -> {
            var reply = catalog.fulfillOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.get("/orders/catalog/orders/{code}", ctx -> {
            var reply = catalog.order(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/projections/orders/{code}", ctx -> {
            var reply = catalog.project(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
    }

}
