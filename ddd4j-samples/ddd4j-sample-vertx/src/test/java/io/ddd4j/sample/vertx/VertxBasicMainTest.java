/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.vertx;

import com.google.common.collect.ImmutableMap;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证默认 Vert.x 应用真实 HTTP 行为和资源关闭顺序。 */
@Testcontainers
class VertxBasicMainTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u901a\u8fc7 <code>CatalogRuntime.start()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param db \u4f20\u7ed9 <code>CatalogRuntime.start()</code> \u7684 <code>MySQLContainer&lt;?&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(), db.getJdbcUrl(), db.getUsername(), db.getPassword()))</code>
     */
    static CatalogEndpoints catalog(MySQLContainer<?> db) {
        return CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(),
                db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }

    /** \u5546\u54c1\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684\u8def\u7531\u6216\u4e1a\u52a1\u7f16\u7801\u3002 */
    static final String GOODS = "{\"spuName\":\"Book\",\"skuCode\":\"S1\",\"skuName\":\"Paperback\",\"shopId\":7,\"salePriceCent\":500}\n";
    /** \u6d4b\u8bd5\u8ba2\u5355\u7684\u56fa\u5b9a\u6807\u8bc6\uff0c\u7528\u4e8e\u7ec4\u7ec7\u548c\u67e5\u8be2\u6d4b\u8bd5\u6570\u636e\u3002 */
    static final String ORDER = "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n";


    /**
     * \u9a8c\u8bc1<code>request(app, "POST", "/api/goods/BOOK", GOODS).statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>data(read).getString("orderState")</code> \u7b49\u4e8e <code>"CONFIRMED"</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void defaultApplicationAcceptsAnonymousOrders() throws Exception {
        VertxOrderApplication app = await(VertxOrderApplication.start(0, catalog(MYSQL)));
        try {
            assertThat(request(app, "POST", "/api/goods/BOOK", GOODS).statusCode()).isEqualTo(201);
            String orderPath = "/api/orders/BASIC-1";
            assertThat(request(app, "POST", orderPath, ORDER).statusCode()).isEqualTo(201);
            assertThat(request(app, "POST", orderPath + "/payment", "{}").statusCode()).isEqualTo(400);
            assertThat(request(app, "POST", orderPath + "/payment", "{\"paidAmountCent\":1000}")
                    .statusCode()).isEqualTo(200);
            SampleHttpTransport.Response read = request(app, "GET", orderPath, null);
            assertThat(read.statusCode()).withFailMessage(read.body()).isEqualTo(200);
            assertThat(data(read).getLong("grossAmountCent")).isEqualTo(1000);
            assertThat(data(read).getString("orderState")).isEqualTo("CONFIRMED");
        } finally {
            await(app.close());
        }
    }

    /**
     * \u9a8c\u8bc1<code>await(VertxOrderApplication.start(app.port(), catalog(MYSQL)))</code> \u629b\u51fa <code>ExecutionException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IOException.class</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void failedListenReturnsTheErrorAndLeavesTheLiveAppAvailable() throws Exception {
        VertxOrderApplication app = await(VertxOrderApplication.start(0, catalog(MYSQL)));
        try {
            assertThrows(ExecutionException.class, () -> await(VertxOrderApplication.start(app.port(), catalog(MYSQL))));
            assertThat(request(app, "GET", "/health", null).statusCode()).isEqualTo(200);
        } finally {
            await(app.close());
        }
        assertThrows(IOException.class, () -> {
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress("127.0.0.1", app.port()), 1000);
            }
        });
    }

    /**
     * \u9a8c\u8bc1<code>api.createGoods("drain", "BOOK", GOODS).getStatus()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>release.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>api.order("drain", "CLOSE-HELD").getStatus()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void closeWaitsForAdmittedBusinessAfterTheClientResets() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CatalogEndpoints api = catalog(MYSQL);
        assertThat(api.createGoods("drain", "BOOK", GOODS).getStatus()).isEqualTo(201);
        CatalogEndpoints held = (CatalogEndpoints) Proxy.newProxyInstance(CatalogEndpoints.class.getClassLoader(),
                new Class<?>[]{CatalogEndpoints.class}, (proxy, method, arguments) -> {
                    if ("placeOrder".equals(method.getName())) {
                        entered.countDown();
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    }
                    return method.invoke(api, arguments);
                });
        VertxOrderApplication app = await(VertxOrderApplication.start(0, held));
        try {
            try (Socket client = new Socket("127.0.0.1", app.port())) {
                client.setSoLinger(true, 0);
                String request = "POST /api/orders/CLOSE-HELD HTTP/1.1\r\nHost: localhost\r\n"
                        + "X-Tenant-Id: drain\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + ORDER.length() + "\r\n\r\n" + ORDER;
                client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            }
            CompletableFuture<Void> closing = app.close().toCompletionStage().toCompletableFuture();
            assertThrows(TimeoutException.class, () -> closing.get(300, TimeUnit.MILLISECONDS));
            assertThat(api.order("drain", "CLOSE-HELD").getStatus()).isEqualTo(404);
            release.countDown();
            closing.get(10, TimeUnit.SECONDS);
            assertThat(api.order("drain", "CLOSE-HELD").getStatus()).isEqualTo(200);
        } finally {
            release.countDown();
            await(app.close());
        }
    }

    /** \u9a8c\u8bc1<code>await(result)</code> \u629b\u51fa <code>ExecutionException.class</code>\uff0c<code>failure.getCause()</code> \u7684\u7c7b\u578b\u4e3a <code>IllegalArgumentException.class</code>\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>original</code>\u3002 */
    @Test
    void invalidPortReturnsFailedFutureWithoutPublishingRuntime() {
        SubjectProvider original = SubjectKit.subjectProvider;
        Future<VertxOrderApplication> result = assertDoesNotThrow(() -> VertxOrderApplication.start(65536, catalog(MYSQL)));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(result));
        assertThat(failure.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertSame(original, SubjectKit.subjectProvider);
    }

    /**
     * \u901a\u8fc7 <code>SampleHttpTransport.request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param app \u4f20\u7ed9 <code>SampleHttpTransport.request()</code> \u7684 <code>VertxOrderApplication</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>SampleHttpTransport.request(base(app) + path, method, Collections.emptyMap(), Map.of("X-Tenant-Id", "basic", "Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : ""), body)</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static SampleHttpTransport.Response request(VertxOrderApplication app, String method, String path, String body)
            throws Exception {
        return SampleHttpTransport.request(base(app) + path, method, Collections.emptyMap(), ImmutableMap.of("X-Tenant-Id", "basic", "Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : ""), body);
    }

    /**
     * \u901a\u8fc7 <code>"http://127.0.0.1:" + app.port()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param app \u4f20\u7ed9 <code>app.port()</code> \u7684 <code>VertxOrderApplication</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>"http://127.0.0.1:" + app.port()</code>
     */
    private static String base(VertxOrderApplication app) {
        return "http://127.0.0.1:" + app.port();
    }

    /**
     * \u521b\u5efa <code>new JsonObject</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new JsonObject(response.body())</code>
     */
    private static JsonObject data(SampleHttpTransport.Response response) {
        return new JsonObject(response.body());
    }

    /**
     * \u901a\u8fc7 <code>result.toCompletionStage()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param result \u4f20\u7ed9 <code>result.toCompletionStage()</code> \u7684 <code>Future&lt;T&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>result.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS)</code>
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    private static <T> T await(Future<T> result) throws Exception {
        return result.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
