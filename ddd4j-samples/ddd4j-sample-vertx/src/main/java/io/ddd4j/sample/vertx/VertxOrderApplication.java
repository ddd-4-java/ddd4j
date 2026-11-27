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

import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.vertx.Ddd4jVertxContext;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
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

/** 基于 MySQL 的商品交易示例。 */
@Slf4j
public final class VertxOrderApplication {

    /** \u4e3a\u5f53\u524d\u793a\u4f8b\u63d0\u4f9b HTTP \u670d\u52a1\u53ca\u5de5\u4f5c\u7ebf\u7a0b\u7684 Vert.x \u5b9e\u4f8b\u3002 */
    private final Vertx vertx = Vertx.vertx();
    /** \u5f53\u524d\u5e94\u7528\u5df2\u63a5\u53d7\u5e76\u987b\u7b49\u5f85\u7ed3\u675f\u7684\u8bf7\u6c42\u5904\u7406\u4fe1\u53f7\u3002 */
    private final Set<CompletableFuture<Void>> accepted = new HashSet<>();
    /** \u5f53\u524d\u793a\u4f8b\u5df2\u88c5\u914d\u7684 Vert.x DDD4J \u8fd0\u884c\u65f6\u3002 */
    private Ddd4jVertxRuntime runtime;
    /** \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u505c\u6b62\u65f6\u5173\u95ed\u7684 Vert.x HTTP \u670d\u52a1\u5668\u3002 */
    private HttpServer server;
    /** \u4fdd\u5b58\u5f53\u524d\u5e94\u7528\u5f02\u6b65\u5173\u95ed\u7ed3\u679c\u7684\u5b8c\u6210\u4fe1\u53f7\u3002 */
    private CompletableFuture<Void> closing;

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>VertxOrderApplication</code> \u5b9e\u4f8b\u3002 */
    private VertxOrderApplication() {
    }

    /**
     * 启动默认匿名订单示例。
     *
     * @param port 监听端口，0 由系统分配
     * @return 已启动的原生应用
     */
    public static Future<VertxOrderApplication> start(int port) {
        return start(port, CatalogRuntime.start());
    }

    /**
     * \u542f\u52a8\u5305\u542b\u5546\u54c1\u4ea4\u6613\u8def\u7531\u7684 Vert.x HTTP \u670d\u52a1\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     * @return \u5e94\u7528\u542f\u52a8\u540e\u7684\u8fd0\u884c\u5b9e\u4f8b\u6216\u5176\u5f02\u6b65\u7ed3\u679c
     */
    static Future<VertxOrderApplication> start(int port, CatalogEndpoints catalog) {
        VertxOrderApplication app = new VertxOrderApplication();
        Promise<VertxOrderApplication> started = Promise.promise();
        try {
            app.runtime = Ddd4jVertxRuntime.create(app.vertx, Collections.emptyList());
            app.runtime.start();
            app.server = app.vertx.createHttpServer().requestHandler(
                    VertxOrderRoutes.router(app.vertx, catalog, AuthenticationMode.DISABLED, app));
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

    /**
     * \u542f\u52a8\u5931\u8d25\u65f6\u6e05\u7406\u90e8\u5206\u88c5\u914d\u7684\u8d44\u6e90\uff0c\u518d\u62a5\u544a\u539f\u59cb\u5931\u8d25\u3002
     * @param started \u62a5\u544a\u5f53\u524d\u5e94\u7528\u662f\u5426\u6210\u529f\u542f\u52a8\u7684\u5f02\u6b65\u53e5\u67c4
     * @param failure \u5bfc\u81f4\u5e94\u7528\u65e0\u6cd5\u5b8c\u6210\u542f\u52a8\u7684\u539f\u59cb\u5f02\u5e38
     */
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

    /**
     * \u62d2\u7edd\u5173\u95ed\u540e\u5230\u8fbe\u7684\u8bf7\u6c42\uff0c\u5e76\u8ffd\u8e2a\u5df2\u63a5\u6536\u7684\u64cd\u4f5c\u76f4\u81f3\u6267\u884c\u5b8c\u6210\u3002
     * @param <T> \u5f53\u524d\u793a\u4f8b\u6240\u4f7f\u7528\u7684\u6570\u636e\u6216\u6846\u67b6\u7c7b\u578b
     * @param context \u5f53\u524d Vert.x \u8def\u7531\u4e0a\u4e0b\u6587
     * @param operation \u5f53\u524d\u8bf7\u6c42\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u6216\u5f85\u6267\u884c\u7684\u5de5\u4f5c\u56de\u8c03
     * @return \u8868\u793a\u8bf7\u6c42\u64cd\u4f5c\u6267\u884c\u7ed3\u679c\u7684\u5f02\u6b65\u5bf9\u8c61
     */
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

    /**
     * \u79fb\u9664\u5df2\u5b8c\u6210\u7684\u8bf7\u6c42\u767b\u8bb0\u5e76\u5524\u9192\u7b49\u5f85\u6e05\u7406\u7684\u5e94\u7528\u751f\u547d\u5468\u671f\u3002
     * @param completed \u8bf7\u6c42\u5904\u7406\u5b8c\u6210\u65f6\u8c03\u7528\u7684\u89c2\u5bdf\u56de\u8c03\u6216\u5b8c\u6210\u4fe1\u53f7
     */
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
        log.info("Vert.x default listening {} (catalog-trade)", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close().toCompletionStage().toCompletableFuture().join();
            log.info("VERTX_DEFAULT_CLOSED");
        }, "ddd4j-vertx-order-shutdown"));
    }
}
