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

import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
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

/**
 * 基于 MySQL 的商品交易示例。
 */
@Slf4j
public final class VertxOrderApplication {

    /**
     * 为当前示例提供 HTTP 服务及工作线程的 Vert.x 实例。
     */
    private final Vertx vertx = Vertx.vertx();
    /**
     * 当前应用已接受并须等待结束的请求处理信号。
     */
    private final Set<CompletableFuture<Void>> accepted = new HashSet<>();
    /**
     * 当前示例已装配的 Vert.x DDD4J 运行时。
     */
    private Ddd4jVertxRuntime runtime;
    /**
     * 由当前应用持有并在停止时关闭的 Vert.x HTTP 服务器。
     */
    private HttpServer server;
    /**
     * 保存当前应用异步关闭结果的完成信号。
     */
    private CompletableFuture<Void> closing;

    /**
     * 限制调用方直接创建 <code>VertxOrderApplication</code> 实例。
     */
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
     * 启动包含商品交易路由的 Vert.x HTTP 服务。
     *
     * @param port    当前 HTTP 示例要监听的端口
     * @param catalog 当前商品交易入口
     * @return 应用启动后的运行实例或其异步结果
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

    /**
     * 启动失败时清理部分装配的资源，再报告原始失败。
     *
     * @param started 报告当前应用是否成功启动的异步句柄
     * @param failure 导致应用无法完成启动的原始异常
     */
    private void failStart(Promise<VertxOrderApplication> started, Throwable failure) {
        close().onComplete(cleaned -> {
            if (cleaned.failed() && cleaned.cause() != failure) {
                failure.addSuppressed(cleaned.cause());
            }
            started.fail(failure);
        });
    }

    /**
     * @return 实际监听端口
     */
    public int port() {
        return server.actualPort();
    }

    /**
     * 拒绝关闭后到达的请求，并追踪已接收的操作直至执行完成。
     *
     * @param <T>       当前示例所使用的数据或框架类型
     * @param context   当前 Vert.x 路由上下文
     * @param operation 当前请求的商品交易操作或待执行的工作回调
     * @return 表示请求操作执行结果的异步对象
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
     * 移除已完成的请求登记并唤醒等待清理的应用生命周期。
     *
     * @param completed 请求处理完成时调用的观察回调或完成信号
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
}
