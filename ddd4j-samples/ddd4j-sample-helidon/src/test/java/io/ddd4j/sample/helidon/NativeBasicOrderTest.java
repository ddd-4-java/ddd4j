/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.helidon;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.helidon.microprofile.server.Server;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证原生 Helidon MP 配置、共享订单和监听器清理。
 */
@Testcontainers
class NativeBasicOrderTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u8c03\u7528 <code>CatalogRuntime.start()</code>\u3001<code>database.getDriverClassName()</code>\u3001<code>database.getJdbcUrl()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param database \u4f20\u7ed9 <code>CatalogRuntime.start()</code> \u7684 <code>MySQLContainer&lt;?&gt;</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CatalogEndpoints</code> \u7ed3\u679c
     */
    static CatalogEndpoints catalog(MySQLContainer<?> database) {
        return CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(), database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
    }

    /**
     * \u6838\u5bf9<code>goods.statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>order.statusCode()</code> \u7b49\u4e8e <code>201</code>\u3002
     * @param server \u4f20\u7ed9 <code>exchange()</code> \u7684 <code>Server</code> \u8f93\u5165
     * @param tenant \u5f53\u524d\u8bf7\u6c42\u7684\u6570\u636e\u79df\u6237
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private static void assertOrder(Server server, String tenant) throws Exception {
        var goods = exchange(server, "POST", "/api/goods/BOOK",
                Map.of("spuName", "Book", "skuCode", "S1", "skuName", "Hardcover",
                        "shopId", 7, "salePriceCent", 500), "", tenant);
        assertEquals(201, goods.statusCode(), goods.body());
        var order = exchange(server, "POST", "/api/orders/BASIC",
                Map.of("shopId", 7, "items", java.util.List.of(Map.of("skuCode", "S1", "quantity", 2))), "", tenant);
        assertEquals(201, order.statusCode(), order.body());
        var payment = exchange(server, "POST", "/api/orders/BASIC/payment", Map.of("paidAmountCent", 1000), "", tenant);
        assertEquals(200, payment.statusCode(), payment.body());
        var read = exchange(server, "GET", "/api/orders/BASIC", null, "", tenant);
        assertEquals(200, read.statusCode(), read.body());
        assertEquals("CONFIRMED", new ObjectMapper().readTree(read.body()).path("orderState").asText());
    }

    /**
     * \u901a\u8fc7 <code>exchange()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param server \u4f20\u7ed9 <code>exchange()</code> \u7684 <code>Server</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>exchange(server, method, path, body, token, "token-test")</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private static SampleHttpTransport.Response exchange(Server server, String method, String path, Object body,
                                                         String token) throws Exception {
        return exchange(server, method, path, body, token, "token-test");
    }

    /**
     * \u8c03\u7528 <code>token.isEmpty()</code>\u3001<code>SampleHttpTransport.request()</code>\u3001<code>server.port()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param server \u4f20\u7ed9 <code>SampleHttpTransport.request()</code> \u7684 <code>Server</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param tenant \u5f53\u524d\u8bf7\u6c42\u7684\u6570\u636e\u79df\u6237
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>SampleHttpTransport.Response</code> \u7ed3\u679c
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static SampleHttpTransport.Response exchange(Server server, String method, String path, Object body,
                                                         String token, String tenant) throws Exception {
        Map<String, String> credentials = token.isEmpty() ? Map.of() : Map.of("Authorization", "Bearer " + token);
        return SampleHttpTransport.request("http://127.0.0.1:" + server.port() + path,
                method, credentials, Map.of("X-Tenant-Id", tenant, "Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : ""),
                Objects.isNull(body) ? null : new ObjectMapper().writeValueAsString(body));
    }

    /**
     * \u9a8c\u8bc1<code>CacheKit.getCache("ddd4j-web-idempotency")</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IOException.class</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void defaultsAllowAnonymousOrderAndFilteredQuery() throws Exception {
        Server server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
        int port = server.port();
        try {
            assertOrder(server, "HELIDON-BASIC");
        } finally {
            server.stop();
        }
        assertNull(CacheKit.getCache("ddd4j-web-idempotency"));
        assertThrows(IOException.class, () -> {
            try (Socket connection = new Socket()) {
                connection.connect(new InetSocketAddress("127.0.0.1", port), 1000);
            }
        });
    }

    /**
     * \u9a8c\u8bc1<code>login.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>token.isEmpty()</code>\u4e3a\u5047\uff0c<code>exchange(server, "GET", "/api/orders/MISSING", null, token).statusCode()</code> \u7b49\u4e8e <code>404</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void explicitRequiredPreservesBearerEntry() throws Exception {
        String setting = "ddd4j.web.authentication-mode";
        String previous = System.getProperty(setting);
        System.setProperty(setting, "REQUIRED");
        try {
            Server server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
            try {
                assertEquals(401, exchange(server, "GET", "/api/orders/DENIED", null, "").statusCode());
                var login = exchange(server, "POST", "/api/auth/tokens/helidon-user", null, "");
                assertEquals(200, login.statusCode(), login.body());
                String token = new ObjectMapper().readTree(login.body()).path("data").path("token").asText();
                assertFalse(token.isEmpty());
                assertEquals(404, exchange(server, "GET", "/api/orders/MISSING", null, token).statusCode());
            } finally {
                server.stop();
            }
        } finally {
            if (Objects.isNull(previous)) {
                System.clearProperty(setting);
            } else {
                System.setProperty(setting, previous);
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>HelidonOrderApplication.start(occupied.getLocalPort(), catalog(MYSQL))</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>original</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void failedNativeBindRestoresTheSubjectRegistration() throws Exception {
        SubjectProvider original = SubjectKit.subjectProvider;
        try (ServerSocket occupied = new ServerSocket(0)) {
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> HelidonOrderApplication.start(occupied.getLocalPort(), catalog(MYSQL)));
            Throwable cause = failure;
            while (Objects.nonNull(cause) && !(cause instanceof BindException)) {
                cause = cause.getCause();
            }
            assertNotNull(cause, "启动失败必须来自已占用的 HTTP 端口");
            assertSame(original, SubjectKit.subjectProvider);
        }
    }

    /**
     * \u9a8c\u8bc1<code>CacheKit.getCache(cacheName)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>callerCache</code>\uff0c<code>CacheKit.get(cacheName, "caller-entry")</code> \u7b49\u4e8e <code>"untouched"</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void callerKeepsItsRegisteredCacheAfterOrderAndShutdown() throws Exception {
        String cacheName = "ddd4j-web-idempotency";
        CacheKit.build(cacheName, 300L);
        Cache<String, Object> callerCache = CacheKit.getCache(cacheName);
        CacheKit.put(cacheName, "caller-entry", "untouched");
        try {
            Server server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
            try {
                assertOrder(server, "HELIDON-BORROWED-CACHE");
                assertSame(callerCache, CacheKit.getCache(cacheName));
            } finally {
                server.stop();
            }
            assertSame(callerCache, CacheKit.getCache(cacheName));
            assertEquals("untouched", CacheKit.get(cacheName, "caller-entry"));
        } finally {
            CacheKit.unregister(cacheName);
        }
    }

    /**
     * \u9a8c\u8bc1<code>CacheKit.get(cacheName, "caller-entry")</code> \u7b49\u4e8e <code>"caller-loaded"</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void callerKeepsLoadingCacheRegisteredDuringSampleShutdown() throws Exception {
        String cacheName = "ddd4j-web-idempotency";
        try {
            Server server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
            try {
                assertOrder(server, "HELIDON-LOADING-CACHE");
                CacheKit.buildWithLoader(cacheName, key -> "caller-loaded", builder -> builder);
            } finally {
                server.stop();
            }
            assertEquals("caller-loaded", CacheKit.get(cacheName, "caller-entry"));
        } finally {
            CacheKit.unregister(cacheName);
        }
    }
}
