/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.kit.lang.MapKit;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.ddd4j.sample.quarkus.shiro.CatalogTradeFixture.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Quarkus RESTEasy Reactive 在共享订单写入前执行原生 Shiro 鉴权。
 */
@QuarkusTest
@QuarkusTestResource(CatalogDatabaseResource.class)
@TestProfile(NativeSharedOrderAuthTest.OrderAuthProfile.class)
class NativeSharedOrderAuthTest {

    /**
     * 构造本组测试输入时复用的固定标识或名称。
     */
    private static final String PROBE_HEADER = "X-Shiro-Completion-Probe";
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
     * 通过 <code>Objects.nonNull()</code> 取得当前测试需要的结果。
     *
     * @param request 待交给被测服务处理的请求
     * @return 当前测试使用的结果，取自 <code>Objects.nonNull(probe) &amp;&amp; probe.id.equals(request.getHeaderString(PROBE_HEADER)) ? probe : null</code>
     */
    private static CompletionProbe probeFor(ContainerRequestContext request) {
        CompletionProbe probe = PROBE.get();
        return Objects.nonNull(probe) && probe.id.equals(request.getHeaderString(PROBE_HEADER)) ? probe : null;
    }

    /**
     * 测试执行前执行 <code>seed()</code> 完成资源准备。
     */
    @BeforeEach
    void prepareGoods() {
        seed(catalog);
    }

    /**
     * 验证<code>catalog.order(TENANT, orderNo).getStatus()</code> 等于 <code>404</code>。
     */
    @Test
    void anonymousAndReadOnlyRequestsDoNotCreateOrders() {
        String orderNo = "denied-" + UUID.randomUUID();
        given().contentType("application/json").body(ORDER).post(ORDERS + orderNo).then().statusCode(401);
        given().header("Authorization", login("user")).contentType("application/json").body(ORDER)
                .post(ORDERS + orderNo).then().statusCode(403);
        assertEquals(404, catalog.order(TENANT, orderNo).getStatus());
        given().get(ORDERS + orderNo).then().statusCode(401);
    }

    /**
     * 验证<code>catalog.order(TENANT, id).getStatus()</code> 等于 <code>200</code>。
     */
    @Test
    void authorizedOrderUsesSharedStoreAndSessionLogoutRevokesAccess() {
        String token = login("admin");
        String id = createOrder(token, "authorized-" + UUID.randomUUID());
        assertEquals(200, catalog.order(TENANT, id).getStatus());
        given().header("Authorization", token).contentType("application/json").header("Idempotency-Key", "checkout-confirmation").body(Map.of("paidAmountCent", 2500))
                .post(ORDERS + id + "/payment").then().statusCode(200).body("orderState", equalTo("CONFIRMED"));
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(200)
                .body("orderNo", equalTo(id)).body("paidAmountCent", equalTo(2500)).body("items", hasSize(1));
        given().header("Authorization", token).post("/auth/logout").then().statusCode(200);
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(401);
    }

    /**
     * 验证<code>probe.original</code>为空，<code>probe.completedSubject</code>为空。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void disconnectedReadClearsRequestSubjectOnItsDispatchThread() throws Exception {
        CompletionProbe probe = disconnectAnAuthenticatedRead(null);
        assertNull(probe.original);
        assertNull(probe.completedSubject);
    }

    /**
     * 验证<code>probe.precedingAuthenticated</code>为真，<code>probe.completedSubject</code> 与…为同一实例 <code>probe.preceding</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void disconnectedReadRestoresThePrecedingNativeSubjectByIdentity() throws Exception {
        CompletionProbe probe = disconnectAnAuthenticatedRead(login("user"));
        assertTrue(probe.precedingAuthenticated);
        assertNotSame(probe.preceding, probe.requestSubject);
        assertSame(probe.preceding, probe.completedSubject);
    }

    /**
     * 读取 <code>probe</code> 供当前测试使用。
     *
     * @param previousToken 本次调用使用的 <code>String</code> 参数 <code>previousToken</code>
     * @return 当前测试使用的结果，取自 <code>probe</code>
     * @throws Exception 准备或读取夹具文件、等待并发操作时失败
     */
    private CompletionProbe disconnectAnAuthenticatedRead(String previousToken) throws Exception {
        String token = login("admin");
        String orderId = createOrder(token, "disconnect-" + UUID.randomUUID());
        CompletionProbe probe = new CompletionProbe(previousToken);
        assertTrue(PROBE.compareAndSet(null, probe));
        try {
            try (Socket client = new Socket(base.getHost(), base.getPort())) {
                client.setSoLinger(true, 0);
                String request = "GET " + ORDERS + orderId + " HTTP/1.1\r\nHost: " + base.getHost()
                        + "\r\nX-Tenant-Id: " + TENANT + "\r\nAuthorization: " + token + "\r\n" + PROBE_HEADER + ": " + probe.id
                        + "\r\nConnection: close\r\n\r\n";
                client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                assertTrue(probe.entered.await(10, TimeUnit.SECONDS), "authenticated RESTEasy dispatch was not held");
                assertTrue(probe.requestAuthenticated, "real Shiro session was not bound to the request");
            }
            probe.release.countDown();
            assertTrue(probe.completed.await(10, TimeUnit.SECONDS), "RESTEasy completion did not run");
            assertSame(probe.dispatchThread, probe.completionThread);
            return probe;
        } finally {
            probe.release.countDown();
            PROBE.compareAndSet(probe, null);
        }
    }

    /**
     * 通过 <code>"Bearer " + token</code> 取得当前测试需要的结果。
     *
     * @param user 传给 <code>MapKit.builder()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>"Bearer " + token</code>
     */
    private String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    /**
     * 启用订单 HTTP 鉴权测试所需的 Quarkus 配置。
     */
    public static class OrderAuthProfile implements QuarkusTestProfile {
    }

    /**
     * 将 <code>id</code>、<code>precedingToken</code>、<code>entered</code>、<code>release</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class CompletionProbe {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id = UUID.randomUUID().toString();
        /**
         * 记录安装当前测试会话之前的令牌。
         */
        private final String precedingToken;
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
         * 观察请求处理期间主体是否已经认证。
         */
        private boolean requestAuthenticated;
        /**
         * 记录受测请求之前的原生登录状态。
         */
        private boolean precedingAuthenticated;
        /**
         * 保存用例修改前的原始对象以备恢复。
         */
        private Subject original;
        /**
         * 进入受测调用前留下的组件或上下文数据。
         */
        private Subject preceding;
        /**
         * 保存请求处理期间可见的主体，用于清理和身份断言。
         */
        private Subject requestSubject;
        /**
         * 请求完成观察回调看到的认证主体。
         */
        private Subject completedSubject;
        /**
         * 记录测试业务实际被分发到的线程。
         */
        private Thread dispatchThread;
        /**
         * 请求结束回调实际使用的线程名。
         */
        private Thread completionThread;

        /**
         * 设置 <code>precedingToken</code>，用于构造 <code>CompletionProbe</code> 测试实例。
         *
         * @param precedingToken 用于初始化 <code>CompletionProbe</code> 的 <code>String</code> 值
         */
        private CompletionProbe(String precedingToken) {
            this.precedingToken = precedingToken;
        }
    }

    /**
     * Uses a separate, real Shiro session to represent an existing request-scope owner.
     */
    @Provider
    @Priority(Priorities.AUTHENTICATION - 1)
    public static class PrecedingSubjectBinding implements ContainerRequestFilter {
        /**
         * 根据本次调用更新 <code>probe.original = ThreadContext.getSubject()</code>、<code>probe.precedingAuthenticated = probe.preceding.isAuthenticated()</code>，用于后续测试步骤。
         *
         * @param request 待交给被测服务处理的请求
         */
        @Override
        public void filter(ContainerRequestContext request) {
            CompletionProbe probe = probeFor(request);
            if (Objects.isNull(probe)) {
                return;
            }
            probe.original = ThreadContext.getSubject();
            if (Objects.nonNull(probe.precedingToken)) {
                probe.preceding = new Subject.Builder(SecurityUtils.getSecurityManager())
                        .sessionId(probe.precedingToken.substring("Bearer ".length())).buildSubject();
                probe.precedingAuthenticated = probe.preceding.isAuthenticated();
                ThreadContext.bind(probe.preceding);
            }
        }
    }

    /**
     * Holds only the designated request and observes the native completion after product cleanup.
     */
    @Provider
    @Priority(Priorities.AUTHENTICATION + 1)
    public static class CompletionObserver implements ContainerRequestFilter {
        /**
         * 根据本次调用更新 <code>probe.dispatchThread = Thread.currentThread()</code>、<code>probe.requestSubject = ThreadContext.getSubject()</code>，用于后续测试步骤。
         *
         * @param request 待交给被测服务处理的请求
         * @throws IOException 读取或写入测试数据时发生 I/O 错误
         */
        @Override
        public void filter(ContainerRequestContext request) throws IOException {
            CompletionProbe probe = probeFor(request);
            if (Objects.isNull(probe)) {
                return;
            }
            probe.dispatchThread = Thread.currentThread();
            probe.requestSubject = ThreadContext.getSubject();
            probe.requestAuthenticated = Objects.nonNull(probe.requestSubject) && probe.requestSubject.isAuthenticated();
            ((ResteasyReactiveContainerRequestContext) request)
                    .getServerRequestContext().registerCompletionCallback(failure -> {
                        probe.completionThread = Thread.currentThread();
                        probe.completedSubject = ThreadContext.getSubject();
                        // Release only the test-owned Subject after observing the product's result.
                        if (Objects.nonNull(probe.preceding) && probe.completedSubject == probe.preceding) {
                            ThreadContext.unbindSubject();
                            if (Objects.nonNull(probe.original)) {
                                ThreadContext.bind(probe.original);
                            }
                        }
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
