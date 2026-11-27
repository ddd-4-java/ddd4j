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

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String PROBE_HEADER = "X-Shiro-Completion-Probe";
    /** \u4f9b\u6d4b\u8bd5\u7ebf\u7a0b\u5b89\u5168\u4fdd\u5b58\u5f53\u524d\u89c2\u5bdf\u7ed3\u679c\u7684\u5f15\u7528\u3002 */
    private static final AtomicReference<CompletionProbe> PROBE = new AtomicReference<>();

    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    @TestHTTPResource
    URI base;
    /** \u5171\u7528\u5546\u54c1\u4ea4\u6613\u7528\u4f8b\u7684 HTTP \u64cd\u4f5c\u5165\u53e3\u3002 */
    @Inject
    CatalogApi catalog;

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
     * \u901a\u8fc7 <code>Objects.nonNull()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Objects.nonNull(probe) &amp;&amp; probe.id.equals(request.getHeaderString(PROBE_HEADER)) ? probe : null</code>
     */
    private static CompletionProbe probeFor(ContainerRequestContext request) {
        CompletionProbe probe = PROBE.get();
        return Objects.nonNull(probe) && probe.id.equals(request.getHeaderString(PROBE_HEADER)) ? probe : null;
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>seed()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void prepareGoods() {
        seed(catalog);
    }

    /** \u9a8c\u8bc1<code>catalog.order(TENANT, orderNo).getStatus()</code> \u7b49\u4e8e <code>404</code>\u3002 */
    @Test
    void anonymousAndReadOnlyRequestsDoNotCreateOrders() {
        String orderNo = "denied-" + UUID.randomUUID();
        given().contentType("application/json").body(ORDER).post(ORDERS + orderNo).then().statusCode(401);
        given().header("Authorization", login("user")).contentType("application/json").body(ORDER)
                .post(ORDERS + orderNo).then().statusCode(403);
        assertEquals(404, catalog.order(TENANT, orderNo).getStatus());
        given().get(ORDERS + orderNo).then().statusCode(401);
    }

    /** \u9a8c\u8bc1<code>catalog.order(TENANT, id).getStatus()</code> \u7b49\u4e8e <code>200</code>\u3002 */
    @Test
    void authorizedOrderUsesSharedStoreAndSessionLogoutRevokesAccess() {
        String token = login("admin");
        String id = createOrder(token, "authorized-" + UUID.randomUUID());
        assertEquals(200, catalog.order(TENANT, id).getStatus());
        given().header("Authorization", token).contentType("application/json").body(Map.of("paidAmountCent", 2500))
                .post(ORDERS + id + "/payment").then().statusCode(200).body("orderState", equalTo("CONFIRMED"));
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(200)
                .body("orderNo", equalTo(id)).body("paidAmountCent", equalTo(2500)).body("items", hasSize(1));
        given().header("Authorization", token).post("/auth/logout").then().statusCode(200);
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(401);
    }

    /**
     * \u9a8c\u8bc1<code>probe.original</code>\u4e3a\u7a7a\uff0c<code>probe.completedSubject</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void disconnectedReadClearsRequestSubjectOnItsDispatchThread() throws Exception {
        CompletionProbe probe = disconnectAnAuthenticatedRead(null);
        assertNull(probe.original);
        assertNull(probe.completedSubject);
    }

    /**
     * \u9a8c\u8bc1<code>probe.precedingAuthenticated</code>\u4e3a\u771f\uff0c<code>probe.completedSubject</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>probe.preceding</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void disconnectedReadRestoresThePrecedingNativeSubjectByIdentity() throws Exception {
        CompletionProbe probe = disconnectAnAuthenticatedRead(login("user"));
        assertTrue(probe.precedingAuthenticated);
        assertNotSame(probe.preceding, probe.requestSubject);
        assertSame(probe.preceding, probe.completedSubject);
    }

    /**
     * \u8bfb\u53d6 <code>probe</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param previousToken \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>String</code> \u53c2\u6570 <code>previousToken</code>
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>probe</code>
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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
     * \u901a\u8fc7 <code>"Bearer " + token</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param user \u4f20\u7ed9 <code>MapKit.builder()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>"Bearer " + token</code>
     */
    private String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    /** \u542f\u7528\u8ba2\u5355 HTTP \u9274\u6743\u6d4b\u8bd5\u6240\u9700\u7684 Quarkus \u914d\u7f6e\u3002 */
    public static class OrderAuthProfile implements QuarkusTestProfile {
    }

    /** \u5c06 <code>id</code>\u3001<code>precedingToken</code>\u3001<code>entered</code>\u3001<code>release</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class CompletionProbe {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id = UUID.randomUUID().toString();
        /** \u8bb0\u5f55\u5b89\u88c5\u5f53\u524d\u6d4b\u8bd5\u4f1a\u8bdd\u4e4b\u524d\u7684\u4ee4\u724c\u3002 */
        private final String precedingToken;
        /** \u7b49\u5f85\u88ab\u6d4b\u64cd\u4f5c\u5df2\u8fdb\u5165\u6267\u884c\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch entered = new CountDownLatch(1);
        /** \u7528\u6765\u63a7\u5236\u6d4b\u8bd5\u7ebf\u7a0b\u9000\u51fa\u7b49\u5f85\u6216\u89c2\u5bdf\u64cd\u4f5c\u5b8c\u6210\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch release = new CountDownLatch(1);
        /** \u7528\u6765\u63a7\u5236\u6d4b\u8bd5\u7ebf\u7a0b\u9000\u51fa\u7b49\u5f85\u6216\u89c2\u5bdf\u64cd\u4f5c\u5b8c\u6210\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch completed = new CountDownLatch(1);
        /** \u89c2\u5bdf\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u4e3b\u4f53\u662f\u5426\u5df2\u7ecf\u8ba4\u8bc1\u3002 */
        private boolean requestAuthenticated;
        /** \u8bb0\u5f55\u53d7\u6d4b\u8bf7\u6c42\u4e4b\u524d\u7684\u539f\u751f\u767b\u5f55\u72b6\u6001\u3002 */
        private boolean precedingAuthenticated;
        /** \u4fdd\u5b58\u7528\u4f8b\u4fee\u6539\u524d\u7684\u539f\u59cb\u5bf9\u8c61\u4ee5\u5907\u6062\u590d\u3002 */
        private Subject original;
        /** \u8fdb\u5165\u53d7\u6d4b\u8c03\u7528\u524d\u7559\u4e0b\u7684\u7ec4\u4ef6\u6216\u4e0a\u4e0b\u6587\u6570\u636e\u3002 */
        private Subject preceding;
        /** \u4fdd\u5b58\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u53ef\u89c1\u7684\u4e3b\u4f53\uff0c\u7528\u4e8e\u6e05\u7406\u548c\u8eab\u4efd\u65ad\u8a00\u3002 */
        private Subject requestSubject;
        /** \u8bf7\u6c42\u5b8c\u6210\u89c2\u5bdf\u56de\u8c03\u770b\u5230\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002 */
        private Subject completedSubject;
        /** \u8bb0\u5f55\u6d4b\u8bd5\u4e1a\u52a1\u5b9e\u9645\u88ab\u5206\u53d1\u5230\u7684\u7ebf\u7a0b\u3002 */
        private Thread dispatchThread;
        /** \u8bf7\u6c42\u7ed3\u675f\u56de\u8c03\u5b9e\u9645\u4f7f\u7528\u7684\u7ebf\u7a0b\u540d\u3002 */
        private Thread completionThread;

        /**
         * \u8bbe\u7f6e <code>precedingToken</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>CompletionProbe</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param precedingToken \u7528\u4e8e\u521d\u59cb\u5316 <code>CompletionProbe</code> \u7684 <code>String</code> \u503c
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
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>probe.original = ThreadContext.getSubject()</code>\u3001<code>probe.precedingAuthenticated = probe.preceding.isAuthenticated()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
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
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>probe.dispatchThread = Thread.currentThread()</code>\u3001<code>probe.requestSubject = ThreadContext.getSubject()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
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
