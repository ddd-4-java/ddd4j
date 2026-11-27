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
    /** \u672a\u6307\u5b9a\u76d1\u542c\u7aef\u53e3\u65f6\u4f7f\u7528\u7684\u9ed8\u8ba4\u7aef\u53e3\u3002 */
    public static final int DEFAULT_PORT = 7001;

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>JavalinCqrsApplication</code> \u5b9e\u4f8b\u3002 */
    private JavalinCqrsApplication() {
    }

    /**
     * \u6309\u542f\u52a8\u53c2\u6570\u914d\u7f6e HTTP \u7aef\u53e3\uff0c\u542f\u52a8 Javalin CQRS \u793a\u4f8b\u5e76\u6ce8\u518c\u5173\u95ed\u94a9\u5b50\u3002
     * @param args \u5e94\u7528\u542f\u52a8\u53c2\u6570
     */
    public static void main(String[] args) {
        Javalin app = startCatalog(parsePort(args), CatalogRuntime::start, new EventBus());
        log.info("Javalin CQRS catalog listening {}", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "javalin-catalog-cqrs-close"));
    }

    /**
     * \u542f\u52a8\u542f\u7528\u4e86\u547d\u4ee4\u8def\u7531\u548c\u4e8b\u4ef6\u53d1\u5e03\u7684\u5546\u54c1\u4ea4\u6613 CQRS \u5e94\u7528\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param initialize \u6309\u6846\u67b6\u53d1\u5e03\u5668\u521b\u5efa\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7684\u5de5\u5382
     * @param events \u8f6c\u53d1\u5f53\u524d\u5e94\u7528\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u603b\u7ebf
     * @return \u5df2\u542f\u52a8\u7684 HTTP \u793a\u4f8b\u670d\u52a1
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
     * \u4ece\u547d\u4ee4\u884c\u8bfb\u53d6\u76d1\u542c\u7aef\u53e3\uff0c\u7f3a\u7701\u65f6\u4f7f\u7528\u793a\u4f8b\u9ed8\u8ba4\u7aef\u53e3\u3002
     * @param args \u5e94\u7528\u542f\u52a8\u65f6\u63d0\u4f9b\u7684\u547d\u4ee4\u884c\u53c2\u6570
     * @return \u5f53\u524d\u5e94\u7528\u542f\u52a8\u65f6\u4f7f\u7528\u7684\u7aef\u53e3
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
     * \u6ce8\u518c\u63a5\u5165 CQRS \u547d\u4ee4\u548c\u6295\u5f71\u7684\u5546\u54c1\u4ea4\u6613\u8def\u7531\u3002
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
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
            var reply = catalog.confirmPayment(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body());
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
