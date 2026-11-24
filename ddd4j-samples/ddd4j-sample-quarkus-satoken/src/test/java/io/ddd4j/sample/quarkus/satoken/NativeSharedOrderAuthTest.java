/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.quarkus.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.kit.lang.MapKit;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quarkus RESTEasy Reactive 上真实 Sa-Token 鉴权、共享订单及断开清理。 */
@QuarkusTest
@TestProfile(NativeSharedOrderAuthTest.OrderAuthProfile.class)
class NativeSharedOrderAuthTest {

    private static final String PROBE_HEADER = "X-Order-Completion-Probe";
    private static final AtomicReference<CompletionProbe> PROBE = new AtomicReference<>();

    @TestHTTPResource
    URI base;

    public static class OrderAuthProfile implements QuarkusTestProfile {
    }

    @Test
    void unauthorizedRequestsCannotReadOrCreateOrders() {
        String orderNo = "anonymous-" + UUID.randomUUID();
        given().contentType("application/json").body(orderBody(orderNo))
                .post("/orders").then().statusCode(401);
        given().get("/orders").then().statusCode(401);
        String readOnly = login("user");
        given().header("Authorization", readOnly).contentType("application/json").body(orderBody(orderNo))
                .post("/orders").then().statusCode(403);
        given().header("Authorization", readOnly).get("/orders").then().statusCode(200);
        assertFalse(sharedStore().findByOrderNo(orderNo).isPresent());
    }

    @Test
    void bearerSchemeCaseDoesNotChangeProtectedOrderReads() {
        String authorization = login("admin");
        String token = authorization.substring("Bearer ".length());
        String orderNo = "bearer-case-" + UUID.randomUUID();
        String id = given().header("Authorization", authorization).contentType("application/json")
                .body(orderBody(orderNo)).post("/orders").then().statusCode(200).extract().path("data.id");

        Map<String, Object> expected = given().header("Authorization", authorization).get("/orders/{id}", id)
                .then().statusCode(200).body("data.id", equalTo(id)).extract().path("data");
        for (String scheme : new String[]{"bearer", "bEaReR"}) {
            Map<String, Object> response = given().header("Authorization", scheme + " " + token)
                    .get("/orders/{id}", id).then().statusCode(200).extract().path("data");
            assertEquals(expected, response);
        }
        given().header("Authorization", "BearerX " + token)
                .get("/orders/{id}", id).then().statusCode(401);
    }

    @Test
    void bearerSeparatorSpacesDoNotChangeProtectedOrderReads() {
        String authorization = login("admin");
        String token = authorization.substring("Bearer ".length());
        String id = given().header("Authorization", authorization).contentType("application/json")
                .body(orderBody("bearer-spaces-" + UUID.randomUUID())).post("/orders")
                .then().statusCode(200).extract().path("data.id");

        Map<String, Object> expected = given().header("Authorization", authorization).get("/orders/{id}", id)
                .then().statusCode(200).body("data.id", equalTo(id)).extract().path("data");
        String nativeTokenHeader = StpUtil.getTokenName();
        given().header(nativeTokenHeader, token).get("/orders/{id}", id)
                .then().statusCode(200).body("data.id", equalTo(id));
        for (String invalid : new String[]{"Bearer", "Bearer   ", "BearerX " + token, "Bearer   invalid-token"}) {
            given().header("Authorization", invalid).get("/orders/{id}", id).then().statusCode(401);
        }
        given().header(nativeTokenHeader, token).header("Authorization", "Bearer   invalid-token")
                .get("/orders/{id}", id).then().statusCode(401);

        String[] bearers = {"Bearer  " + token, "bEaReR    " + token};
        for (int index = 0; index < bearers.length; index++) {
            Response response = given().header("Authorization", bearers[index]).get("/orders/{id}", id);
            assertEquals(200, response.statusCode(), "Bearer separator variant " + (index + 1));
            assertEquals(expected, response.jsonPath().getMap("data"));
        }
    }

    @Test
    void authorizedOrderUsesSharedStoreThroughPaymentAndLogout() {
        String token = login("admin");
        String orderNo = "authorized-" + UUID.randomUUID();
        String id = given().header("Authorization", token).contentType("application/json").body(orderBody(orderNo))
                .post("/orders").then().statusCode(200).body("data.status", equalTo("DRAFT"))
                .extract().path("data.id");
        assertEquals(id, sharedStore().findByOrderNo(orderNo).get().id());

        given().header("Authorization", token).contentType("application/json")
                .body("{\"goodsId\":\"item-1\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":12.50}")
                .post("/orders/{id}/lines", id).then().statusCode(200).body("data.lines", hasSize(1));
        given().header("Authorization", token).contentType("application/json")
                .post("/orders/{id}/pay", id).then().statusCode(200).body("data.status", equalTo("PAID"));
        given().header("Authorization", token).queryParam("orderNo", orderNo).get("/orders/by-order-no")
                .then().statusCode(200).body("data.id", equalTo(id)).body("data.currency", equalTo("CNY"))
                .body("data.lines", hasSize(1));
        given().header("Authorization", token).queryParam("buyerId", "buyer-1").queryParam("status", "PAID")
                .get("/orders").then().statusCode(200).body("data.find { it.id == '" + id + "' }.status", equalTo("PAID"));
        assertEquals("PAID", sharedStore().findById(id).get().status().name());

        given().header("Authorization", token).post("/auth/logout").then().statusCode(200);
        given().header("Authorization", token).get("/orders/{id}", id).then().statusCode(401);
        given().header("Authorization", token).contentType("application/json").body(orderBody("revoked-" + orderNo))
                .post("/orders").then().statusCode(401);
        assertFalse(sharedStore().findByOrderNo("revoked-" + orderNo).isPresent());
    }

    @Test
    void disconnectedOrderReadCleansNativeContextOnDispatchThread() throws Exception {
        String token = login("admin");
        CompletionProbe probe = new CompletionProbe();
        assertTrue(PROBE.compareAndSet(null, probe));
        try {
            try (Socket socket = new Socket(base.getHost(), base.getPort())) {
                socket.setSoLinger(true, 0);
                String request = "GET /orders HTTP/1.1\r\nHost: " + base.getHost() + "\r\nAuthorization: " + token
                        + "\r\n" + PROBE_HEADER + ": " + probe.id + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTrue(probe.entered.await(10, TimeUnit.SECONDS), "authenticated dispatch was not held");
                assertTrue(probe.nativeLoginAtEntry);
            }
            probe.release.countDown();
            assertTrue(probe.completed.await(10, TimeUnit.SECONDS), "RESTEasy completion hook did not run");
            assertSame(probe.dispatchThread, probe.completionThread);
            assertFalse(probe.contextPresentAtCompletion);
        } finally {
            probe.release.countDown();
            PROBE.compareAndSet(probe, null);
        }
    }

    private static InMemoryOrderAdapters sharedStore() {
        return CDI.current().select(InMemoryOrderAdapters.class).get();
    }

    private static String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    private static Map<String, String> orderBody(String orderNo) {
        return MapKit.builder("orderNo", orderNo).put("buyerId", "buyer-1").put("buyerName", "Alice").build();
    }

    private static final class CompletionProbe {
        private final String id = UUID.randomUUID().toString();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private Thread dispatchThread;
        private Thread completionThread;
        private boolean nativeLoginAtEntry;
        private boolean contextPresentAtCompletion;
    }

    /** Holds only the designated real request and observes native completion without cleaning it. */
    @Provider
    @Priority(Priorities.AUTHENTICATION + 1)
    public static class CompletionObserver implements ContainerRequestFilter {
        @Override
        public void filter(ContainerRequestContext context) throws IOException {
            CompletionProbe probe = PROBE.get();
            if (java.util.Objects.isNull(probe) || !probe.id.equals(context.getHeaderString(PROBE_HEADER))) {
                return;
            }
            probe.dispatchThread = Thread.currentThread();
            probe.nativeLoginAtEntry = StpUtil.isLogin();
            ((ResteasyReactiveContainerRequestContext) context)
                    .getServerRequestContext().registerCompletionCallback(failure -> {
                probe.completionThread = Thread.currentThread();
                probe.contextPresentAtCompletion = SaManager.getSaTokenContext().isValid();
                probe.completed.countDown();
            });
            probe.entered.countDown();
            try {
                if (!probe.release.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Completion probe was not released");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("Completion probe was interrupted", failure);
            }
        }
    }
}
