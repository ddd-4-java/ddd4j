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
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.vertx.Ddd4jVertxContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** 无外部基础设施的共享订单示例。 */
@Slf4j
public final class VertxOrderApplication {

    private final Vertx vertx = Vertx.vertx();
    private final Set<CompletableFuture<Void>> accepted = new HashSet<>();
    private Ddd4jVertxRuntime runtime;
    private HttpServer server;
    private CompletableFuture<Void> closing;

    private VertxOrderApplication() {
    }

    /**
     * 启动默认匿名订单示例。
     *
     * @param port 监听端口，0 由系统分配
     * @return 已启动的原生应用
     */
    public static Future<VertxOrderApplication> start(int port) {
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        return start(port, new OrderApplicationService(adapters, adapters, adapters, adapters, adapters));
    }

    static Future<VertxOrderApplication> start(int port, OrderApplicationService service) {
        VertxOrderApplication app = new VertxOrderApplication();
        Promise<VertxOrderApplication> started = Promise.promise();
        try {
            app.runtime = Ddd4jVertxRuntime.create(app.vertx, Collections.emptyList());
            app.runtime.start();
            app.server = app.vertx.createHttpServer().requestHandler(
                    VertxOrderRoutes.router(app.vertx, service, AuthenticationMode.DISABLED, app));
            app.server.listen(port, "127.0.0.1").onComplete(result -> {
                if (result.succeeded()) {
                    started.complete(app);
                } else {
                    app.failStart(started, result.cause());
                }
            });
        } catch (RuntimeException | Error failure) {
            app.failStart(started, failure);
        }
        return started.future();
    }

    private void failStart(Promise<VertxOrderApplication> started, Throwable failure) {
        close().onComplete(cleaned -> {
            if (cleaned.failed() && cleaned.cause() != failure) {
                failure.addSuppressed(cleaned.cause());
            }
            started.fail(failure);
        });
    }

    /** @return 实际监听端口 */
    public int port() {
        return server.actualPort();
    }

    synchronized <T> Future<T> execute(RoutingContext context, Callable<T> operation) {
        if (Objects.nonNull(closing)) {
            return Future.failedFuture(new WebStatusException(503, "application closing"));
        }
        CompletableFuture<Void> completed = new CompletableFuture<>();
        accepted.add(completed);
        try {
            Future<T> result = Ddd4jVertxContext.executeBlocking(context, operation);
            result.onComplete(ignored -> finish(completed));
            return result;
        } catch (RuntimeException | Error failure) {
            finish(completed);
            throw failure;
        }
    }

    private synchronized void finish(CompletableFuture<Void> completed) {
        accepted.remove(completed);
        completed.complete(null);
    }

    /**
     * 停止接收新业务，等待已提交的操作结束，再关闭 HTTP 和运行时资源。
     *
     * @return 当前关闭操作的结果
     */
    public synchronized Future<Void> close() {
        if (Objects.isNull(closing)) {
            CompletableFuture<Void> drained = CompletableFuture.allOf(accepted.toArray(new CompletableFuture<?>[0]));
            log.info("VERTX_DEFAULT_DRAINING accepted={}", accepted.size());
            closing = CompletableFuture.runAsync(() -> {
                drained.join();
                log.info("VERTX_DEFAULT_DRAINED");
                try {
                    if (Objects.nonNull(server)) {
                        server.close().toCompletionStage().toCompletableFuture().join();
                    }
                } finally {
                    try {
                        if (Objects.nonNull(runtime)) {
                            runtime.close();
                        }
                    } finally {
                        vertx.close().toCompletionStage().toCompletableFuture().join();
                    }
                }
            });
        }
        return Future.fromCompletionStage(closing);
    }

    /**
     * 启动默认示例，端口可由 sample.port 设置。
     *
     * @param args 保留的启动参数
     * @throws Exception 启动失败
     */
    public static void main(String[] args) throws Exception {
        VertxOrderApplication app = start(Integer.getInteger("sample.port", 8084))
                .toCompletionStage().toCompletableFuture().get();
        log.info("Vert.x default listening {} (demo-memory)", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close().toCompletionStage().toCompletableFuture().join();
            log.info("VERTX_DEFAULT_CLOSED");
        }, "ddd4j-vertx-order-shutdown"));
    }
}
