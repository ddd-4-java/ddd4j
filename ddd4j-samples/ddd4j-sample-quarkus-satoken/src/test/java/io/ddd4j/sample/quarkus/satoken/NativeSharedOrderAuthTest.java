/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.quarkus.satoken;

import com.google.common.collect.ImmutableMap;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.kit.lang.MapKit;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.quarkus.satoken.rbac.RbacConfig;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.ddd4j.sample.quarkus.satoken.CatalogTradeFixture.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Quarkus RESTEasy Reactive 上真实 Sa-Token 鉴权、共享订单及断开清理。
 */
@QuarkusTest
@QuarkusTestResource(CatalogDatabaseResource.class)
class NativeSharedOrderAuthTest {

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String PROBE_HEADER = "X-Order-Completion-Probe";
    /** \u4f9b\u6d4b\u8bd5\u7ebf\u7a0b\u5b89\u5168\u4fdd\u5b58\u5f53\u524d\u89c2\u5bdf\u7ed3\u679c\u7684\u5f15\u7528\u3002 */
    private static final AtomicReference<CompletionProbe> PROBE = new AtomicReference<>();

    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    @TestHTTPResource
    URI base;
    /** \u5171\u7528\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u7684 HTTP \u64cd\u4f5c\u5165\u53e3\u3002 */
    @Inject
    CatalogApi catalog;
    /** \u5f53\u524d\u793a\u4f8b\u7528\u6765\u89e3\u6790\u89d2\u8272\u7f16\u8f91\u548c\u6743\u9650\u5206\u914d\u7684\u914d\u7f6e\u3002 */
    @Inject
    RbacConfig rbac;

    /**
     * \u8c03\u7528 <code>given()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param authorization \u4f20\u7ed9 <code>given()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>String</code> \u7ed3\u679c
     */
    private static String createOrder(String authorization, String id) {
        return given().header("Authorization", authorization).contentType("application/json")
                .body(ORDER).post(ORDERS + id).then().statusCode(201).extract().path("orderNo");
    }

    /**
     * \u901a\u8fc7 <code>"Bearer " + token</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param user \u4f20\u7ed9 <code>MapKit.builder()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>"Bearer " + token</code>
     */
    private static String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>rbac.reset()</code>\u3001<code>seed()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void prepareGoods() {
        rbac.reset();
        seed(catalog);
    }

    /** \u9a8c\u8bc1<code>catalog.order(TENANT, orderNo).getStatus()</code> \u7b49\u4e8e <code>404</code>\u3002 */
    @Test
    void unauthorizedRequestsCannotReadOrCreateOrders() {
        String orderNo = "anonymous-" + UUID.randomUUID();
        given().contentType("application/json").body(ORDER).post(ORDERS + orderNo).then().statusCode(401);
        given().get(ORDERS + orderNo).then().statusCode(401);
        String readOnly = login("user");
        given().header("Authorization", readOnly).contentType("application/json").body(ORDER)
                .post(ORDERS + orderNo).then().statusCode(403);
        given().header("Authorization", readOnly).get(ORDERS + orderNo).then().statusCode(404);
        assertEquals(404, catalog.order(TENANT, orderNo).getStatus());
    }

    /** \u9a8c\u8bc1<code>response</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void bearerSchemeCaseDoesNotChangeProtectedOrderReads() {
        String authorization = login("admin");
        String token = authorization.substring("Bearer ".length());
        String orderNo = "bearer-case-" + UUID.randomUUID();
        String id = createOrder(authorization, orderNo);
        Map<String, Object> expected = given().header("Authorization", authorization).get(ORDERS + id)
                .then().statusCode(200).body("orderNo", equalTo(id)).extract().as(Map.class);
        for (String scheme : new String[]{"bearer", "bEaReR"}) {
            Map<String, Object> response = given().header("Authorization", scheme + " " + token)
                    .get(ORDERS + id).then().statusCode(200).extract().as(Map.class);
            assertEquals(expected, response);
        }
        given().header("Authorization", "BearerX " + token)
                .get(ORDERS + id).then().statusCode(401);
    }

    /** \u9a8c\u8bc1<code>response.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.as(Map.class)</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void bearerSeparatorSpacesDoNotChangeProtectedOrderReads() {
        String authorization = login("admin");
        String token = authorization.substring("Bearer ".length());
        String id = createOrder(authorization, "bearer-spaces-" + UUID.randomUUID());
        Map<String, Object> expected = given().header("Authorization", authorization).get(ORDERS + id)
                .then().statusCode(200).body("orderNo", equalTo(id)).extract().as(Map.class);
        String nativeTokenHeader = StpUtil.getTokenName();
        given().header(nativeTokenHeader, token).get(ORDERS + id)
                .then().statusCode(200).body("orderNo", equalTo(id));
        for (String invalid : new String[]{"Bearer", "Bearer   ", "BearerX " + token, "Bearer   invalid-token"}) {
            given().header("Authorization", invalid).get(ORDERS + id).then().statusCode(401);
        }
        given().header(nativeTokenHeader, token).header("Authorization", "Bearer   invalid-token")
                .get(ORDERS + id).then().statusCode(401);

        String[] bearers = {"Bearer  " + token, "bEaReR    " + token};
        for (int index = 0; index < bearers.length; index++) {
            Response response = given().header("Authorization", bearers[index]).get(ORDERS + id);
            assertEquals(200, response.statusCode(), "Bearer separator variant " + (index + 1));
            assertEquals(expected, response.as(Map.class));
        }
    }

    /** \u9a8c\u8bc1<code>catalog.order(TENANT, id).getStatus()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>catalog.order(TENANT, "revoked-" + id).getStatus()</code> \u7b49\u4e8e <code>404</code>\u3002 */
    @Test
    void authorizedOrderUsesSharedStoreThroughPaymentAndLogout() {
        String token = login("admin");
        String id = createOrder(token, "authorized-" + UUID.randomUUID());
        assertEquals(200, catalog.order(TENANT, id).getStatus());
        given().header("Authorization", token).contentType("application/json").body(ImmutableMap.of("paidAmountCent", 2500))
                .post(ORDERS + id + "/payment").then().statusCode(200).body("orderState", equalTo("CONFIRMED"));
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(200)
                .body("orderNo", equalTo(id)).body("items", hasSize(1)).body("paidAmountCent", equalTo(2500));
        given().header("Authorization", token).post("/auth/logout").then().statusCode(200);
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(401);
        given().header("Authorization", token).contentType("application/json").body(ORDER)
                .post(ORDERS + "revoked-" + id).then().statusCode(401);
        assertEquals(404, catalog.order(TENANT, "revoked-" + id).getStatus());
    }

    /**
     * \u9a8c\u8bc1<code>PROBE.compareAndSet(null, probe)</code>\u4e3a\u771f\uff0c<code>probe.entered.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>probe.contextPresentAtCompletion</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void disconnectedOrderReadCleansNativeContextOnDispatchThread() throws Exception {
        String token = login("admin");
        CompletionProbe probe = new CompletionProbe();
        assertTrue(PROBE.compareAndSet(null, probe));
        try {
            try (Socket socket = new Socket(base.getHost(), base.getPort())) {
                socket.setSoLinger(true, 0);
                String request = "GET " + ORDERS + "disconnect HTTP/1.1\r\nHost: " + base.getHost() + "\r\nX-Tenant-Id: " + TENANT + "\r\nAuthorization: " + token
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

    /** \u5c06 <code>id</code>\u3001<code>entered</code>\u3001<code>release</code>\u3001<code>completed</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class CompletionProbe {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id = UUID.randomUUID().toString();
        /** \u7b49\u5f85\u88ab\u6d4b\u64cd\u4f5c\u5df2\u8fdb\u5165\u6267\u884c\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch entered = new CountDownLatch(1);
        /** \u7528\u6765\u63a7\u5236\u6d4b\u8bd5\u7ebf\u7a0b\u9000\u51fa\u7b49\u5f85\u6216\u89c2\u5bdf\u64cd\u4f5c\u5b8c\u6210\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch release = new CountDownLatch(1);
        /** \u7528\u6765\u63a7\u5236\u6d4b\u8bd5\u7ebf\u7a0b\u9000\u51fa\u7b49\u5f85\u6216\u89c2\u5bdf\u64cd\u4f5c\u5b8c\u6210\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch completed = new CountDownLatch(1);
        /** \u8bb0\u5f55\u6d4b\u8bd5\u4e1a\u52a1\u5b9e\u9645\u88ab\u5206\u53d1\u5230\u7684\u7ebf\u7a0b\u3002 */
        private Thread dispatchThread;
        /** \u8bf7\u6c42\u7ed3\u675f\u56de\u8c03\u5b9e\u9645\u4f7f\u7528\u7684\u7ebf\u7a0b\u540d\u3002 */
        private Thread completionThread;
        /** \u8fdb\u5165\u4e1a\u52a1\u56de\u8c03\u65f6\u89c2\u5bdf\u5230\u7684\u539f\u751f\u767b\u5f55\u72b6\u6001\u3002 */
        private boolean nativeLoginAtEntry;
        /** \u8bb0\u5f55\u8bf7\u6c42\u5b8c\u6210\u56de\u8c03\u662f\u5426\u8fd8\u80fd\u770b\u5230\u4e1a\u52a1\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u3002 */
        private boolean contextPresentAtCompletion;
    }

    /**
     * Holds only the designated real request and observes native completion without cleaning it.
     */
    @Provider
    @Priority(Priorities.AUTHENTICATION + 1)
    public static class CompletionObserver implements ContainerRequestFilter {
        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>probe.dispatchThread = Thread.currentThread()</code>\u3001<code>probe.nativeLoginAtEntry = StpUtil.isLogin()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
         * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
         */
        @Override
        public void filter(ContainerRequestContext context) throws IOException {
            CompletionProbe probe = PROBE.get();
            if (java.util.Objects.isNull(probe) || !probe.id.equals(context.getHeaderString(PROBE_HEADER))) {
                return;
            }
            probe.dispatchThread = Thread.currentThread();
            probe.nativeLoginAtEntry = StpUtil.isLogin();
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

    /** 为测试场景提供 <code>javax.ws.rs.container.ContainerResponseFilter</code> 的实现。 */
    @Provider
    @Priority(Priorities.AUTHENTICATION - 1)
    public static class CompletionResponseObserver implements javax.ws.rs.container.ContainerResponseFilter {
        /**
         * 执行 <code>filter</code>，调用 <code>PROBE.get</code>、<code>id.equals</code>、<code>request.getHeaderString</code>。
         *
         * @param request 待发送或处理的 HTTP 请求
         * @param response 当前收到的 HTTP 响应
         */
        @Override
        public void filter(ContainerRequestContext request, javax.ws.rs.container.ContainerResponseContext response) {
            CompletionProbe probe = PROBE.get();
            if (java.util.Objects.isNull(probe) || !probe.id.equals(request.getHeaderString(PROBE_HEADER))) {
                return;
            }
            probe.completionThread = Thread.currentThread();
            probe.contextPresentAtCompletion = SaManager.getSaTokenContext().isValid();
            probe.completed.countDown();
        }
    }
}
