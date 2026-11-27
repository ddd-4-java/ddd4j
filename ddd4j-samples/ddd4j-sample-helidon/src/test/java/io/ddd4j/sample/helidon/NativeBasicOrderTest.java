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
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 调用 <code>CatalogRuntime.start()</code>、<code>database.getDriverClassName()</code>、<code>database.getJdbcUrl()</code> 执行当前夹具操作。
     *
     * @param database 传给 <code>CatalogRuntime.start()</code> 的 <code>MySQLContainer&lt;?&gt;</code> 输入
     * @return 提供给后续契约断言的 <code>CatalogEndpoints</code> 结果
     */
    static CatalogEndpoints catalog(MySQLContainer<?> database) {
        return CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(), database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
    }

    /**
     * 核对<code>goods.statusCode()</code> 等于 <code>201</code>，<code>order.statusCode()</code> 等于 <code>201</code>。
     *
     * @param server 传给 <code>exchange()</code> 的 <code>Server</code> 输入
     * @param tenant 当前请求的数据租户
     * @throws Exception 处理测试数据的序列化时失败
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
     * 通过 <code>exchange()</code> 取得当前测试需要的结果。
     *
     * @param server 传给 <code>exchange()</code> 的 <code>Server</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param body   当前请求或响应中要读取的正文
     * @param token  从测试登录获得的会话凭据
     * @return 当前测试使用的结果，取自 <code>exchange(server, method, path, body, token, "token-test")</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private static SampleHttpTransport.Response exchange(Server server, String method, String path, Object body,
                                                         String token) throws Exception {
        return exchange(server, method, path, body, token, "token-test");
    }

    /**
     * 调用 <code>token.isEmpty()</code>、<code>SampleHttpTransport.request()</code>、<code>server.port()</code> 执行当前夹具操作。
     *
     * @param server 传给 <code>SampleHttpTransport.request()</code> 的 <code>Server</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param body   当前请求或响应中要读取的正文
     * @param token  从测试登录获得的会话凭据
     * @param tenant 当前请求的数据租户
     * @return 提供给后续契约断言的 <code>SampleHttpTransport.Response</code> 结果
     * @throws Exception 处理测试数据的序列化、发起测试请求时失败
     */
    private static SampleHttpTransport.Response exchange(Server server, String method, String path, Object body,
                                                         String token, String tenant) throws Exception {
        Map<String, String> credentials = token.isEmpty() ? Map.of() : Map.of("Authorization", "Bearer " + token);
        return SampleHttpTransport.request("http://127.0.0.1:" + server.port() + path,
                method, credentials, Map.of("X-Tenant-Id", tenant, "Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : ""),
                Objects.isNull(body) ? null : new ObjectMapper().writeValueAsString(body));
    }

    /**
     * 验证<code>CacheKit.getCache("ddd4j-web-idempotency")</code>为空，被测调用 抛出 <code>IOException.class</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>login.statusCode()</code> 等于 <code>200</code>，<code>token.isEmpty()</code>为假，<code>exchange(server, "GET", "/api/orders/MISSING", null, token).statusCode()</code> 等于 <code>404</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
     * 验证<code>HelidonOrderApplication.start(occupied.getLocalPort(), catalog(MYSQL))</code> 抛出 <code>RuntimeException.class</code>，<code>SubjectKit.subjectProvider</code> 与…为同一实例 <code>original</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>CacheKit.getCache(cacheName)</code> 与…为同一实例 <code>callerCache</code>，<code>CacheKit.get(cacheName, "caller-entry")</code> 等于 <code>"untouched"</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>CacheKit.get(cacheName, "caller-entry")</code> 等于 <code>"caller-loaded"</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
