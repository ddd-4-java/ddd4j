package io.ddd4j.sample.javalin.cqrs;

import io.ddd4j.sample.order.adapter.http.Reply;
import com.google.common.collect.ImmutableList;
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
    public static final int DEFAULT_PORT = 7001;

    private JavalinCqrsApplication() {
    }

    public static void main(String[] args) {
        Javalin app = startCatalog(parsePort(args), CatalogRuntime::start, new EventBus());
        log.info("Javalin CQRS catalog listening {}", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "javalin-catalog-cqrs-close"));
    }

    static Javalin startCatalog(int port, Function<DomainEventPublisher, CatalogApi> initialize, EventBus events) {
        CatalogApi api = initialize.apply(new GuiceDomainEventPublisher(events));
        CatalogCqrs catalog = new CatalogCqrs(api, new GuiceCommandBus(ImmutableList.of(CatalogCqrs.executor(api))));
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });
        app.routes(() -> catalogRoutes(catalog));
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

    private static void catalogRoutes(CatalogCqrs catalog) {
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/goods/{code}", ctx -> {
            Reply reply = catalog.createGoods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.get("/orders/catalog/goods/{code}", ctx -> {
            Reply reply = catalog.goods(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{code}", ctx -> {
            Reply reply = catalog.placeOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("code"), ctx.body());
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{orderNo}/payment", ctx -> {
            Reply reply = catalog.confirmPayment(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"), ctx.body());
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{orderNo}/cancel", ctx -> {
            Reply reply = catalog.cancelOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/orders/{orderNo}/fulfill", ctx -> {
            Reply reply = catalog.fulfillOrder(ctx.header("X-Tenant-Id"), ctx.pathParam("orderNo"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.get("/orders/catalog/orders/{code}", ctx -> {
            Reply reply = catalog.order(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
        io.javalin.apibuilder.ApiBuilder.post("/orders/catalog/projections/orders/{code}", ctx -> {
            Reply reply = catalog.project(ctx.header("X-Tenant-Id"), ctx.pathParam("code"));
            ctx.status(reply.getStatus()).contentType("application/json").result(reply.getBody());
        });
    }

}
