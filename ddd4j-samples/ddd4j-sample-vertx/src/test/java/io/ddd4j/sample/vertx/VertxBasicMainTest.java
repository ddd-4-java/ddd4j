/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.vertx;

import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证默认 Vert.x 应用真实 HTTP 行为和资源关闭顺序。
 */
@Testcontainers
class VertxBasicMainTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 商品测试所使用的路由或业务编码。
     */
    static final String GOODS = """
            {"spuName":"Book","skuCode":"S1","skuName":"Paperback","shopId":7,"salePriceCent":500}
            """;
    /**
     * 测试订单的固定标识，用于组织和查询测试数据。
     */
    static final String ORDER = """
            {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
            """;

    /**
     * 通过 <code>CatalogRuntime.start()</code> 取得当前测试需要的结果。
     *
     * @param db 传给 <code>CatalogRuntime.start()</code> 的 <code>MySQLContainer&lt;?&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(), db.getJdbcUrl(), db.getUsername(), db.getPassword()))</code>
     */
    static CatalogEndpoints catalog(MySQLContainer<?> db) {
        return CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(),
                db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }

    /**
     * 通过 <code>SampleHttpTransport.request()</code> 取得当前测试需要的结果。
     *
     * @param app    传给 <code>SampleHttpTransport.request()</code> 的 <code>VertxOrderApplication</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param body   当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>SampleHttpTransport.request(base(app) + path, method, Collections.emptyMap(), Map.of("X-Tenant-Id", "basic"), body)</code>
     * @throws Exception 发起测试请求时失败
     */
    private static SampleHttpTransport.Response request(VertxOrderApplication app, String method, String path, String body)
            throws Exception {
        return SampleHttpTransport.request(base(app) + path, method, Collections.emptyMap(), Map.of("X-Tenant-Id", "basic"), body);
    }

    /**
     * 通过 <code>"http://127.0.0.1:" + app.port()</code> 取得当前测试需要的结果。
     *
     * @param app 传给 <code>app.port()</code> 的 <code>VertxOrderApplication</code> 输入
     * @return 当前测试使用的结果，取自 <code>"http://127.0.0.1:" + app.port()</code>
     */
    private static String base(VertxOrderApplication app) {
        return "http://127.0.0.1:" + app.port();
    }

    /**
     * 创建 <code>new JsonObject</code> 供当前测试路径调用。
     *
     * @param response 当前请求已收到的响应
     * @return 当前测试使用的结果，取自 <code>new JsonObject(response.body())</code>
     */
    private static JsonObject data(SampleHttpTransport.Response response) {
        return new JsonObject(response.body());
    }

    /**
     * 通过 <code>result.toCompletionStage()</code> 取得当前测试需要的结果。
     *
     * @param <T>    当前契约测试所使用的 Java 类型
     * @param result 传给 <code>result.toCompletionStage()</code> 的 <code>Future&lt;T&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>result.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS)</code>
     * @throws Exception 等待并发操作时失败
     */
    private static <T> T await(Future<T> result) throws Exception {
        return result.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /**
     * 验证<code>request(app, "POST", "/api/goods/BOOK", GOODS).statusCode()</code> 等于 <code>201</code>，<code>data(read).getString("orderState")</code> 等于 <code>"CONFIRMED"</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>await(VertxOrderApplication.start(app.port(), catalog(MYSQL)))</code> 抛出 <code>ExecutionException.class</code>，被测调用 抛出 <code>IOException.class</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>api.createGoods("drain", "BOOK", GOODS).getStatus()</code> 等于 <code>201</code>，<code>release.await(10, TimeUnit.SECONDS)</code>为真，<code>api.order("drain", "CLOSE-HELD").getStatus()</code> 等于 <code>200</code>。
     *
     * @throws Exception 准备或读取夹具文件、反射访问被测成员、等待并发操作时失败
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

    /**
     * 验证<code>await(result)</code> 抛出 <code>ExecutionException.class</code>，<code>failure.getCause()</code> 的类型为 <code>IllegalArgumentException.class</code>，<code>SubjectKit.subjectProvider</code> 与…为同一实例 <code>original</code>。
     */
    @Test
    void invalidPortReturnsFailedFutureWithoutPublishingRuntime() {
        SubjectProvider original = SubjectKit.subjectProvider;
        Future<VertxOrderApplication> result = assertDoesNotThrow(() -> VertxOrderApplication.start(65536, catalog(MYSQL)));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(result));
        assertThat(failure.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertSame(original, SubjectKit.subjectProvider);
    }
}
