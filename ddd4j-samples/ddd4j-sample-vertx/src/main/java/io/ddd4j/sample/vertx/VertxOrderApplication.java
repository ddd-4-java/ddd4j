package io.ddd4j.sample.vertx;

import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

import java.util.List;
import java.util.Objects;

/**
 * 共享 Order 业务内核的 Vert.x 启动入口。
 */
public final class VertxOrderApplication {

    private VertxOrderApplication() {
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.create(vertx, List.of());
        runtime.start();
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        OrderApplicationService applicationService = new OrderApplicationService(adapters, adapters, adapters,
                adapters, adapters);
        Router router = VertxOrderRoutes.router(vertx, applicationService);
        vertx.createHttpServer().requestHandler(router).listen(8084)
                .onSuccess(server -> registerShutdownHook(vertx, runtime, server));
    }

    private static void registerShutdownHook(Vertx vertx, Ddd4jVertxRuntime runtime, HttpServer server) {
        Objects.requireNonNull(vertx, "vertx must not be null");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.close()
                .onComplete(ignored -> {
                    runtime.close();
                    vertx.close();
                }), "ddd4j-vertx-order-shutdown"));
    }
}
