/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
