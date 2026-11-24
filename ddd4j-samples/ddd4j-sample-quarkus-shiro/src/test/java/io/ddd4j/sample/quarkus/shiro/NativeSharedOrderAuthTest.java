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
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Quarkus RESTEasy Reactive 在共享订单写入前执行原生 Shiro 鉴权。 */
@QuarkusTest
@TestProfile(NativeSharedOrderAuthTest.OrderAuthProfile.class)
class NativeSharedOrderAuthTest {

    private static final String PROBE_HEADER = "X-Shiro-Completion-Probe";
    private static final AtomicReference<CompletionProbe> PROBE = new AtomicReference<>();

    @TestHTTPResource
    URI base;

    public static class OrderAuthProfile implements QuarkusTestProfile {
    }

    @Test
    void anonymousAndReadOnlyRequestsDoNotCreateOrders() {
        String orderNo = "denied-" + UUID.randomUUID();
        given().contentType("application/json").body(orderBody(orderNo))
                .post("/orders").then().statusCode(401);
        given().header("Authorization", login("user")).contentType("application/json").body(orderBody(orderNo))
                .post("/orders").then().statusCode(403);
        assertFalse(sharedStore().findByOrderNo(orderNo).isPresent());
        given().get("/orders").then().statusCode(200);
    }

    @Test
    void authorizedOrderUsesSharedStoreAndSessionLogoutRevokesAccess() {
        String token = login("admin");
        String orderNo = "allowed-" + UUID.randomUUID();
        String id = given().header("Authorization", token).contentType("application/json").body(orderBody(orderNo))
                .post("/orders").then().statusCode(200).body("data.status", equalTo("DRAFT"))
                .extract().path("data.id");
        assertEquals(id, sharedStore().findByOrderNo(orderNo).get().id());

        given().header("Authorization", token).contentType("application/json")
                .body("{\"goodsId\":\"item-1\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":12.50}")
                .post("/orders/{id}/lines", id).then().statusCode(200).body("data.lines", hasSize(1));
        given().header("Authorization", token).contentType("application/json").post("/orders/{id}/pay", id)
                .then().statusCode(200).body("data.status", equalTo("PAID"));
        given().header("Authorization", token).queryParam("orderNo", orderNo).get("/orders/by-order-no")
                .then().statusCode(200).body("data.id", equalTo(id))
                .body("data.currency", equalTo("CNY")).body("data.lines", hasSize(1));
        assertEquals("PAID", sharedStore().findById(id).get().status().name());

        given().header("Authorization", token).contentType("application/json")
                .post("/auth/logout").then().statusCode(200);
        given().header("Authorization", token).get("/orders/{id}", id).then().statusCode(401);
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
        String orderId = given().header("Authorization", token).contentType("application/json")
                .body(orderBody("disconnect-" + UUID.randomUUID())).post("/orders")
                .then().statusCode(200).extract().path("data.id");
        CompletionProbe probe = new CompletionProbe(previousToken);
        assertTrue(PROBE.compareAndSet(null, probe));
        try {
            try (Socket client = new Socket(base.getHost(), base.getPort())) {
                client.setSoLinger(true, 0);
                String request = "GET /orders/" + orderId + " HTTP/1.1\r\nHost: " + base.getHost()
                        + "\r\nAuthorization: " + token + "\r\n" + PROBE_HEADER + ": " + probe.id
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

    private InMemoryOrderAdapters sharedStore() {
        return CDI.current().select(InMemoryOrderAdapters.class).get();
    }

    private String login(String user) {
        Map<String, String> credentials = MapKit.builder("username", user).put("password", "123456").build();
        String token = given().contentType("application/json").body(credentials)
                .post("/auth/login").then().statusCode(200).extract().path("token");
        return "Bearer " + token;
    }

    private Map<String, String> orderBody(String orderNo) {
        return MapKit.builder("orderNo", orderNo).put("buyerId", "buyer-1").put("buyerName", "Alice").build();
    }

    private static CompletionProbe probeFor(ContainerRequestContext request) {
        CompletionProbe probe = PROBE.get();
        return Objects.nonNull(probe) && probe.id.equals(request.getHeaderString(PROBE_HEADER)) ? probe : null;
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

    /** Uses a separate, real Shiro session to represent an existing request-scope owner. */
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

    /** Holds only the designated request and observes the native completion after product cleanup. */
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
