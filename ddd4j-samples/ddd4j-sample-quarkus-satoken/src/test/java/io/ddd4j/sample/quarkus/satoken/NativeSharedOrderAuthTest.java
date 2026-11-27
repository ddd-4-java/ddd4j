/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.quarkus.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.kit.lang.MapKit;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.quarkus.satoken.rbac.RbacConfig;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext;
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

    /**
     * 构造本组测试输入时复用的固定标识或名称。
     */
    private static final String PROBE_HEADER = "X-Order-Completion-Probe";
    /**
     * 供测试线程安全保存当前观察结果的引用。
     */
    private static final AtomicReference<CompletionProbe> PROBE = new AtomicReference<>();

    /**
     * 本地测试服务的基础地址。
     */
    @TestHTTPResource
    URI base;
    /**
     * 共用商品交易用例的 HTTP 操作入口。
     */
    @Inject
    CatalogApi catalog;
    /**
     * 当前示例用来解析角色编辑和权限分配的配置。
     */
    @Inject
    RbacConfig rbac;

    /**
     * 调用 <code>given()</code> 执行当前夹具操作。
     *
     * @param authorization 传给 <code>given()</code> 的 <code>String</code> 输入
     * @param id            当前要查找的测试记录标识
     * @return 提供给后续契约断言的 <code>String</code> 结果
     */
    private static String createOrder(String authorization, String id) {
        return given().header("Authorization", authorization).contentType("application/json")
                .body(ORDER).post(ORDERS + id).then().statusCode(201).extract().path("orderNo");
    }

    /**
     * 通过 <code>"Bearer " + token</code> 取得当前测试需要的结果。
     *
     * @param user 传给 <code>MapKit.builder()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>"Bearer " + token</code>
     */
    private static String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    /**
     * 测试执行前执行 <code>rbac.reset()</code>、<code>seed()</code> 完成资源准备。
     */
    @BeforeEach
    void prepareGoods() {
        rbac.reset();
        seed(catalog);
    }

    /**
     * 验证<code>catalog.order(TENANT, orderNo).getStatus()</code> 等于 <code>404</code>。
     */
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

    /**
     * 验证<code>response</code> 等于 <code>expected</code>。
     */
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

    /**
     * 验证<code>response.statusCode()</code> 等于 <code>200</code>，<code>response.as(Map.class)</code> 等于 <code>expected</code>。
     */
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

    /**
     * 验证<code>catalog.order(TENANT, id).getStatus()</code> 等于 <code>200</code>，<code>catalog.order(TENANT, "revoked-" + id).getStatus()</code> 等于 <code>404</code>。
     */
    @Test
    void authorizedOrderUsesSharedStoreThroughPaymentAndLogout() {
        String token = login("admin");
        String id = createOrder(token, "authorized-" + UUID.randomUUID());
        assertEquals(200, catalog.order(TENANT, id).getStatus());
        given().header("Authorization", token).contentType("application/json").body(Map.of("paidAmountCent", 2500))
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
     * 验证<code>PROBE.compareAndSet(null, probe)</code>为真，<code>probe.entered.await(10, TimeUnit.SECONDS)</code>为真，<code>probe.contextPresentAtCompletion</code>为假。
     *
     * @throws Exception 准备或读取夹具文件、等待并发操作时失败
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

    /**
     * 将 <code>id</code>、<code>entered</code>、<code>release</code>、<code>completed</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class CompletionProbe {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id = UUID.randomUUID().toString();
        /**
         * 等待被测操作已进入执行的同步门闩。
         */
        private final CountDownLatch entered = new CountDownLatch(1);
        /**
         * 用来控制测试线程退出等待或观察操作完成的同步门闩。
         */
        private final CountDownLatch release = new CountDownLatch(1);
        /**
         * 用来控制测试线程退出等待或观察操作完成的同步门闩。
         */
        private final CountDownLatch completed = new CountDownLatch(1);
        /**
         * 记录测试业务实际被分发到的线程。
         */
        private Thread dispatchThread;
        /**
         * 请求结束回调实际使用的线程名。
         */
        private Thread completionThread;
        /**
         * 进入业务回调时观察到的原生登录状态。
         */
        private boolean nativeLoginAtEntry;
        /**
         * 记录请求完成回调是否还能看到业务线程上下文。
         */
        private boolean contextPresentAtCompletion;
    }

    /**
     * Holds only the designated real request and observes native completion without cleaning it.
     */
    @Provider
    @Priority(Priorities.AUTHENTICATION + 1)
    public static class CompletionObserver implements ContainerRequestFilter {
        /**
         * 根据本次调用更新 <code>probe.dispatchThread = Thread.currentThread()</code>、<code>probe.nativeLoginAtEntry = StpUtil.isLogin()</code>，用于后续测试步骤。
         *
         * @param context 当前框架保存请求或运行时状态的上下文
         * @throws IOException 读取或写入测试数据时发生 I/O 错误
         */
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
