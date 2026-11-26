/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.helidon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static CatalogEndpoints catalog(MySQLContainer<?> database) {
        return CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(), database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
    }

    private static void assertOrder(Server server, String tenant) throws Exception {
        SampleHttpTransport.Response goods = exchange(server, "POST", "/api/goods/BOOK",
                ImmutableMap.of("spuName", "Book", "skuCode", "S1", "skuName", "Hardcover",
                        "shopId", 7, "salePriceCent", 500), "", tenant);
        assertEquals(201, goods.statusCode(), goods.body());
        SampleHttpTransport.Response order = exchange(server, "POST", "/api/orders/BASIC",
                ImmutableMap.of("shopId", 7, "items", ImmutableList.of(ImmutableMap.of("skuCode", "S1", "quantity", 2))), "", tenant);
        assertEquals(201, order.statusCode(), order.body());
        SampleHttpTransport.Response payment = exchange(server, "POST", "/api/orders/BASIC/payment", ImmutableMap.of("paidAmountCent", 1000), "", tenant);
        assertEquals(200, payment.statusCode(), payment.body());
        SampleHttpTransport.Response read = exchange(server, "GET", "/api/orders/BASIC", null, "", tenant);
        assertEquals(200, read.statusCode(), read.body());
        assertEquals("CONFIRMED", new ObjectMapper().readTree(read.body()).path("orderState").asText());
    }

    private static SampleHttpTransport.Response exchange(Server server, String method, String path, Object body,
                                                         String token) throws Exception {
        return exchange(server, method, path, body, token, "token-test");
    }

    private static SampleHttpTransport.Response exchange(Server server, String method, String path, Object body,
                                                         String token, String tenant) throws Exception {
        Map<String, String> credentials = token.isEmpty() ? ImmutableMap.of() : ImmutableMap.of("Authorization", "Bearer " + token);
        return SampleHttpTransport.request("http://127.0.0.1:" + server.port() + path,
                method, credentials, ImmutableMap.of("X-Tenant-Id", tenant),
                Objects.isNull(body) ? null : new ObjectMapper().writeValueAsString(body));
    }

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

    @Test
    void explicitRequiredPreservesBearerEntry() throws Exception {
        String setting = "ddd4j.web.authentication-mode";
        String previous = System.getProperty(setting);
        System.setProperty(setting, "REQUIRED");
        try {
            Server server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
            try {
                assertEquals(401, exchange(server, "GET", "/api/orders/DENIED", null, "").statusCode());
                SampleHttpTransport.Response login = exchange(server, "POST", "/api/auth/tokens/helidon-user", null, "");
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
