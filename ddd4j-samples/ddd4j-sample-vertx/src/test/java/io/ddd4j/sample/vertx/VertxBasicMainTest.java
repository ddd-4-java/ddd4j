/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.vertx;

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
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static CatalogEndpoints catalog(MySQLContainer<?> db) {
        return CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(),
                db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }

    static final String GOODS = """
            {"spuName":"Book","skuCode":"S1","skuName":"Paperback","shopId":7,"salePriceCent":500}
            """;
    static final String ORDER = """
            {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
            """;


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

    @Test
    void invalidPortReturnsFailedFutureWithoutPublishingRuntime() {
        SubjectProvider original = SubjectKit.subjectProvider;
        Future<VertxOrderApplication> result = assertDoesNotThrow(() -> VertxOrderApplication.start(65536, catalog(MYSQL)));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(result));
        assertThat(failure.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertSame(original, SubjectKit.subjectProvider);
    }

    private static SampleHttpTransport.Response request(VertxOrderApplication app, String method, String path, String body)
            throws Exception {
        return SampleHttpTransport.request(base(app) + path, method, Collections.emptyMap(), Map.of("X-Tenant-Id", "basic"), body);
    }

    private static String base(VertxOrderApplication app) {
        return "http://127.0.0.1:" + app.port();
    }

    private static JsonObject data(SampleHttpTransport.Response response) {
        return new JsonObject(response.body());
    }

    private static <T> T await(Future<T> result) throws Exception {
        return result.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
