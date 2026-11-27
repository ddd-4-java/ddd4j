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
package io.ddd4j.web.vertx;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.idempotency.IdempotencyLease;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>Vertx.vertx()</code>、<code>Router.router()</code>、<code>router.route()</code> 验证 <code>Ddd4jVertxEarlyResponse</code> 的运行行为。
 */
class Ddd4jVertxEarlyResponseTest {

    /**
     * 验证<code>client.getResponseCode()</code> 等于 <code>204</code>，<code>releasedOnWorker.get(5, TimeUnit.SECONDS)</code>为真，<code>dispatched.get()</code>为假。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void serverSideEarlyResponseReleasesUnusedLeaseOnWorker() throws Exception {
        Vertx vertx = Vertx.vertx();
        CompletableFuture<Runnable> endResponse = new CompletableFuture<>();
        CompletableFuture<Void> ended = new CompletableFuture<>();
        CompletableFuture<Boolean> releasedOnWorker = new CompletableFuture<>();
        AtomicBoolean dispatched = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        IdempotencyGuard guard = new IdempotencyGuard() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                endResponse.join().run();
                try {
                    ended.get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                return true;
            }

            @Override
            public void complete(String key) {
                completed.set(true);
            }

            @Override
            public void release(String key) {
                releasedOnWorker.complete(Context.isOnWorkerThread());
            }
        };
        try {
            Router router = Router.router(vertx);
            router.route().handler(context -> {
                Context eventLoop = Vertx.currentContext();
                endResponse.complete(() -> eventLoop.runOnContext(ignored -> context.response().setStatusCode(204).end()));
                context.addEndHandler(ignored -> eventLoop.runOnContext(next -> ended.complete(null)));
                context.next();
            });
            new Ddd4jVertxWeb(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                    new DefaultWebExceptionTranslator(), new WebIdempotencyLifecycle(guard),
                    io.vertx.core.json.Json::encode).install(router);
            router.post("/early").handler(context -> dispatched.set(true));
            HttpServer server = vertx.createHttpServer().requestHandler(router).listen(0, "127.0.0.1")
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            HttpURLConnection client = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + server.actualPort() + "/early").openConnection();
            try {
                client.setRequestMethod("POST");
                client.setRequestProperty("Idempotency-Key", "early");
                client.setReadTimeout(5000);
                assertEquals(204, client.getResponseCode());
                assertTrue(releasedOnWorker.get(5, TimeUnit.SECONDS));
                assertFalse(completed.get());
                assertFalse(dispatched.get());
            } finally {
                client.disconnect();
            }
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 验证<code>serverObservedTermination.get(5, TimeUnit.SECONDS)</code>为真，<code>releasedOnWorker.get(5, TimeUnit.SECONDS)</code> 等于 <code>disconnectedLease</code>，<code>dispatched.get()</code> 等于 <code>1</code>。
     *
     * @throws Exception 准备或读取夹具文件、等待并发操作、发起测试请求时失败
     */
    @Test
    void tcpResetDuringIdempotencyAcquisitionReleasesLeaseAndAllowsSameKeyRetry() throws Exception {
        String cacheName = "vertx-tcp-reset";
        String idempotencyKey = "same-key-after-reset";
        CacheKit.build(cacheName, 300L);
        Vertx vertx = Vertx.vertx();
        CompletableFuture<IdempotencyLease> firstAcquisitionHeld = new CompletableFuture<>();
        CompletableFuture<Boolean> serverObservedTermination = new CompletableFuture<>();
        CompletableFuture<Void> returnAcquiredLease = new CompletableFuture<>();
        CompletableFuture<IdempotencyLease> releasedOnWorker = new CompletableFuture<>();
        CompletableFuture<IdempotencyLease> completedLease = new CompletableFuture<>();
        AtomicInteger dispatched = new AtomicInteger();
        CacheIdempotencyGuard delegate = new CacheIdempotencyGuard(cacheName);
        IdempotencyGuard guard = new IdempotencyGuard() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return delegate.acquire(key, ttl);
            }

            @Override
            public Optional<IdempotencyLease> acquireLease(String key, Duration ttl) {
                Optional<IdempotencyLease> lease = delegate.acquireLease(key, ttl);
                if (lease.isPresent() && firstAcquisitionHeld.complete(lease.get())) {
                    try {
                        returnAcquiredLease.get(10, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }
                return lease;
            }

            @Override
            public void complete(String key) {
                delegate.complete(key);
            }

            @Override
            public void complete(IdempotencyLease lease) {
                delegate.complete(lease);
                completedLease.complete(lease);
            }

            @Override
            public void release(String key) {
                delegate.release(key);
            }

            @Override
            public void release(IdempotencyLease lease) {
                delegate.release(lease);
                if (Context.isOnWorkerThread()) {
                    releasedOnWorker.complete(lease);
                } else {
                    releasedOnWorker.completeExceptionally(new AssertionError("lease released outside a worker"));
                }
            }
        };
        try {
            Router router = Router.router(vertx);
            router.route().handler(context -> {
                if ("true".equals(context.request().getHeader("X-Test-Reset"))) {
                    Context eventLoop = Vertx.currentContext();
                    context.addEndHandler(end -> eventLoop.runOnContext(ignored ->
                            serverObservedTermination.complete(end.failed())));
                }
                context.next();
            });
            new Ddd4jVertxWeb(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                    new DefaultWebExceptionTranslator(), new WebIdempotencyLifecycle(guard),
                    io.vertx.core.json.Json::encode).install(router);
            router.post("/reset").handler(context -> {
                dispatched.incrementAndGet();
                context.response().setStatusCode(201).end();
            });
            HttpServer server = vertx.createHttpServer().requestHandler(router).listen(0, "127.0.0.1")
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            IdempotencyLease disconnectedLease;
            try (Socket client = new Socket("127.0.0.1", server.actualPort())) {
                client.setSoLinger(true, 0);
                String request = "POST /reset HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n"
                        + "Idempotency-Key: " + idempotencyKey + "\r\nX-Test-Reset: true\r\n\r\n";
                client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                disconnectedLease = firstAcquisitionHeld.get(5, TimeUnit.SECONDS);
            }
            assertTrue(serverObservedTermination.get(5, TimeUnit.SECONDS),
                    "The server must observe connection failure before acquisition returns");
            returnAcquiredLease.complete(null);
            assertEquals(disconnectedLease, releasedOnWorker.get(5, TimeUnit.SECONDS));
            assertEquals(0, dispatched.get());
            assertFalse(completedLease.isDone());

            assertEquals(201, postWithIdempotencyKey(server.actualPort(), idempotencyKey));
            IdempotencyLease retryLease = completedLease.get(5, TimeUnit.SECONDS);
            assertEquals(disconnectedLease.key(), retryLease.key());
            assertFalse(disconnectedLease.ownerToken().equals(retryLease.ownerToken()));
            assertEquals(409, postWithIdempotencyKey(server.actualPort(), idempotencyKey));
            assertEquals(1, dispatched.get());
        } finally {
            returnAcquiredLease.complete(null);
            try {
                vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } finally {
                CacheKit.unregister(cacheName);
            }
        }
    }

    /**
     * 通过 <code>client.getResponseCode()</code> 取得当前测试需要的结果。
     *
     * @param port 测试服务监听的本地端口
     * @param key  用于在测试上下文或缓存中索引数据的键
     * @return 当前测试使用的结果，取自 <code>client.getResponseCode()</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private int postWithIdempotencyKey(int port, String key) throws Exception {
        HttpURLConnection client = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/reset")
                .openConnection();
        try {
            client.setRequestMethod("POST");
            client.setRequestProperty("Idempotency-Key", key);
            client.setConnectTimeout(5000);
            client.setReadTimeout(5000);
            return client.getResponseCode();
        } finally {
            client.disconnect();
        }
    }
}
