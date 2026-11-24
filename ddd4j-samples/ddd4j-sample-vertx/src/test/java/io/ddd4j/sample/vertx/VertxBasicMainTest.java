/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.ddd4j.sample.vertx;

import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
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
class VertxBasicMainTest {

    @Test
    void defaultApplicationAcceptsAnonymousOrders() throws Exception {
        VertxOrderApplication app = await(VertxOrderApplication.start(0));
        try {
            String json = "{\"orderNo\":\"BASIC-1\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}";
            SampleHttpTransport.Response created = request(app, "POST", "/api/orders", json);
            assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);
            String id = data(created).getString("id");
            String orderPath = "/api/orders/" + id;
            SampleHttpTransport.Response line = request(app, "POST", orderPath + "/lines",
                    "{\"goodsId\":\"g1\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":5}");
            assertThat(line.statusCode()).withFailMessage(line.body()).isEqualTo(200);
            String payPath = orderPath + "/pay";
            assertThat(request(app, "POST", payPath, null).statusCode()).isEqualTo(400);
            SampleHttpTransport.Response paid = SampleHttpTransport.request(base(app) + payPath, "POST",
                    Collections.emptyMap(), Collections.singletonMap("Idempotency-Key", "vertx-basic-1"), null);
            assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
            SampleHttpTransport.Response read = request(app, "GET", orderPath, null);
            assertThat(read.statusCode()).withFailMessage(read.body()).isEqualTo(200);
            assertThat(data(read).getDouble("totalAmount")).isEqualTo(10);
            assertThat(data(read).getString("status")).isEqualTo("PAID");
        } finally {
            await(app.close());
        }
    }

    @Test
    void failedListenReturnsTheErrorAndLeavesTheLiveAppAvailable() throws Exception {
        VertxOrderApplication app = await(VertxOrderApplication.start(0));
        try {
            assertThrows(ExecutionException.class, () -> await(VertxOrderApplication.start(app.port())));
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
        InMemoryOrderAdapters memory = new InMemoryOrderAdapters();
        OrderApplicationService heldService = new OrderApplicationService(memory, memory, memory, memory, memory) {
            @Override
            public Order create(CreateOrderCommand command) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test did not release admitted order");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("close interrupted the admitted order", failure);
                }
                return super.create(command);
            }
        };
        VertxOrderApplication app = await(VertxOrderApplication.start(0, heldService));
        try {
            try (Socket client = new Socket("127.0.0.1", app.port())) {
                client.setSoLinger(true, 0);
                String json = "{\"orderNo\":\"CLOSE-HELD\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}";
                String request = "POST /api/orders HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + json.length() + "\r\n\r\n" + json;
                client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            }
            CompletableFuture<Void> closing = app.close().toCompletionStage().toCompletableFuture();
            assertThrows(TimeoutException.class, () -> closing.get(300, TimeUnit.MILLISECONDS));
            assertThat(memory.findByOrderNo("CLOSE-HELD")).isEmpty();
            release.countDown();
            closing.get(10, TimeUnit.SECONDS);
            assertThat(memory.findByOrderNo("CLOSE-HELD")).isPresent();
        } finally {
            release.countDown();
            await(app.close());
        }
    }

    @Test
    void invalidPortReturnsFailedFutureWithoutPublishingRuntime() {
        SubjectProvider original = SubjectKit.subjectProvider;
        Future<VertxOrderApplication> result = assertDoesNotThrow(() -> VertxOrderApplication.start(65536));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(result));
        assertThat(failure.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertSame(original, SubjectKit.subjectProvider);
    }

    private static SampleHttpTransport.Response request(VertxOrderApplication app, String method, String path, String body)
            throws Exception {
        return SampleHttpTransport.request(base(app) + path, method, Collections.emptyMap(), Collections.emptyMap(), body);
    }

    private static String base(VertxOrderApplication app) {
        return "http://127.0.0.1:" + app.port();
    }

    private static JsonObject data(SampleHttpTransport.Response response) {
        return new JsonObject(response.body()).getJsonObject("data");
    }

    private static <T> T await(Future<T> result) throws Exception {
        return result.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
