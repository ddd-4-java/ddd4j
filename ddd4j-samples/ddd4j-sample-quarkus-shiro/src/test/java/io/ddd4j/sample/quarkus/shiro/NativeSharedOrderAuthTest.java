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

import com.google.common.collect.ImmutableMap;
import io.ddd4j.kit.lang.MapKit;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
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

    private static final String PROBE_HEADER = "X-Shiro-Completion-Probe";
    private static final AtomicReference<CompletionProbe> PROBE = new AtomicReference<>();

    @TestHTTPResource
    URI base;
    @Inject
    CatalogApi catalog;

    private static String createOrder(String authorization, String id) {
        return given().header("Authorization", authorization).contentType("application/json")
                .body(ORDER).post(ORDERS + id).then().statusCode(201).extract().path("orderNo");
    }

    private static CompletionProbe probeFor(ContainerRequestContext request) {
        CompletionProbe probe = PROBE.get();
        return Objects.nonNull(probe) && probe.id.equals(request.getHeaderString(PROBE_HEADER)) ? probe : null;
    }

    @BeforeEach
    void prepareGoods() {
        seed(catalog);
    }

    @Test
    void anonymousAndReadOnlyRequestsDoNotCreateOrders() {
        String orderNo = "denied-" + UUID.randomUUID();
        given().contentType("application/json").body(ORDER).post(ORDERS + orderNo).then().statusCode(401);
        given().header("Authorization", login("user")).contentType("application/json").body(ORDER)
                .post(ORDERS + orderNo).then().statusCode(403);
        assertEquals(404, catalog.order(TENANT, orderNo).getStatus());
        given().get(ORDERS + orderNo).then().statusCode(401);
    }

    @Test
    void authorizedOrderUsesSharedStoreAndSessionLogoutRevokesAccess() {
        String token = login("admin");
        String id = createOrder(token, "authorized-" + UUID.randomUUID());
        assertEquals(200, catalog.order(TENANT, id).getStatus());
        given().header("Authorization", token).contentType("application/json").body(ImmutableMap.of("paidAmountCent", 2500))
                .post(ORDERS + id + "/payment").then().statusCode(200).body("orderState", equalTo("CONFIRMED"));
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(200)
                .body("orderNo", equalTo(id)).body("paidAmountCent", equalTo(2500)).body("items", hasSize(1));
        given().header("Authorization", token).post("/auth/logout").then().statusCode(200);
        given().header("Authorization", token).get(ORDERS + id).then().statusCode(401);
    }

    @Test
    void disconnectedReadClearsRequestSubjectOnItsDispatchThread() throws Exception {
        CompletionProbe probe = disconnectAnAuthenticatedRead(null);
        assertNull(probe.original);
        assertNull(probe.completedSubject);
    }

    @Test
    void disconnectedReadRestoresThePrecedingNativeSubjectByIdentity() throws Exception {
        CompletionProbe probe = disconnectAnAuthenticatedRead(login("user"));
        assertTrue(probe.precedingAuthenticated);
        assertNotSame(probe.preceding, probe.requestSubject);
        assertSame(probe.preceding, probe.completedSubject);
    }

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

    private String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    public static class OrderAuthProfile implements QuarkusTestProfile {
    }

    private static final class CompletionProbe {
        private final String id = UUID.randomUUID().toString();
        private final String precedingToken;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private boolean requestAuthenticated;
        private boolean precedingAuthenticated;
        private Subject original;
        private Subject preceding;
        private Subject requestSubject;
        private Subject completedSubject;
        private Thread dispatchThread;
        private Thread completionThread;

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
        @Override
        public void filter(ContainerRequestContext request) throws IOException {
            CompletionProbe probe = probeFor(request);
            if (Objects.isNull(probe)) {
                return;
            }
            probe.dispatchThread = Thread.currentThread();
            probe.requestSubject = ThreadContext.getSubject();
            probe.requestAuthenticated = Objects.nonNull(probe.requestSubject) && probe.requestSubject.isAuthenticated();
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

    @Provider
    @Priority(Priorities.AUTHENTICATION - 1)
    public static class CompletionResponseObserver implements javax.ws.rs.container.ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext request, javax.ws.rs.container.ContainerResponseContext response) {
            CompletionProbe probe = probeFor(request);
            if (Objects.isNull(probe)) {
                return;
            }
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
        }
    }
}
