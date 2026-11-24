/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.helidon;

import io.ddd4j.core.cache.Cache;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证原生 Helidon MP 配置、共享订单和监听器清理。 */
class NativeBasicOrderTest {

    @Test
    void defaultsAllowAnonymousOrderAndFilteredQuery() {
        Server server = HelidonOrderApplication.start(0);
        int port = server.port();
        try {
            SampleContractAssertions.defaultOrder(driver(server), "HELIDON-BASIC");
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
    void explicitRequiredPreservesBearerEntry() {
        String setting = "ddd4j.web.authentication-mode";
        String previous = System.getProperty(setting);
        System.setProperty(setting, "REQUIRED");
        try {
            Server server = HelidonOrderApplication.start(0);
            try {
                EnvelopeOrderHttpDriver http = driver(server);
                SecurityException denied = assertThrows(SecurityException.class,
                        () -> http.create(new CreateOrderCommand("DENIED", "buyer-1", "Alice")));
                assertEquals("HTTP 401", denied.getMessage());
                String token = http.request("POST", "/api/auth/tokens/helidon-legacy", null, Collections.emptyMap())
                        .path("token").asText();
                assertFalse(token.isEmpty());
                String id = http.request("POST", "/api/orders", new CreateOrderCommand("LEGACY", "buyer-1", "Alice"),
                        Collections.singletonMap("Authorization", "Bearer " + token)).path("id").asText();
                assertFalse(id.isEmpty());
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
                    () -> HelidonOrderApplication.start(occupied.getLocalPort()));
            Throwable cause = failure;
            while (Objects.nonNull(cause) && !(cause instanceof BindException)) {
                cause = cause.getCause();
            }
            assertNotNull(cause, "启动失败必须来自已占用的 HTTP 端口");
            assertSame(original, SubjectKit.subjectProvider);
        }
    }

    @Test
    void callerKeepsItsRegisteredCacheAfterOrderAndShutdown() {
        String cacheName = "ddd4j-web-idempotency";
        CacheKit.build(cacheName, 300L);
        Cache<String, Object> callerCache = CacheKit.getCache(cacheName);
        CacheKit.put(cacheName, "caller-entry", "untouched");
        try {
            Server server = HelidonOrderApplication.start(0);
            try {
                SampleContractAssertions.defaultOrder(driver(server), "HELIDON-BORROWED-CACHE");
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
    void callerKeepsLoadingCacheRegisteredDuringSampleShutdown() {
        String cacheName = "ddd4j-web-idempotency";
        try {
            Server server = HelidonOrderApplication.start(0);
            try {
                SampleContractAssertions.defaultOrder(driver(server), "HELIDON-LOADING-CACHE");
                CacheKit.buildWithLoader(cacheName, key -> "caller-loaded", builder -> builder);
            } finally {
                server.stop();
            }
            assertEquals("caller-loaded", CacheKit.get(cacheName, "caller-entry"));
        } finally {
            CacheKit.unregister(cacheName);
        }
    }

    private static EnvelopeOrderHttpDriver driver(Server server) {
        return new EnvelopeOrderHttpDriver("http://127.0.0.1:" + server.port(), "/api/orders", Collections.emptyMap());
    }
}
