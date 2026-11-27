package io.ddd4j.sample.micronaut.shiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 原生 HTTP/会话/订单验证；观测器只读线程状态，绝不清理所测状态。
 */
@Testcontainers
class NativeAuthHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final ConcurrentLinkedQueue<Boolean> CLEAN = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<String> THREADS = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<String> FINISHED = new ConcurrentLinkedQueue<>();
    static volatile CountDownLatch entered = new CountDownLatch(1);
    static volatile CountDownLatch release = new CountDownLatch(1);

    static EmbeddedServer start(boolean observe) {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "sample.test.observer", observe, "catalog.jdbc.url", MYSQL.getJdbcUrl(),
                "catalog.db.user", MYSQL.getUsername(), "catalog.db.password", MYSQL.getPassword(),
                "ddd4j.web.idempotency-enabled", false));
    }

    static boolean nativePresent() {
        return Objects.nonNull(org.apache.shiro.util.ThreadContext.getSubject());
    }

    static Object nativeState() {
        return org.apache.shiro.util.ThreadContext.getSubject();
    }

    private static SampleHttpTransport.Response readOrder(String url, String tenantId, Map<String, String> headers)
            throws java.io.IOException {
        var requestHeaders = new java.util.HashMap<>(headers);
        requestHeaders.put("X-Tenant-Id", tenantId);
        return SampleHttpTransport.request(url, "GET", requestHeaders, Map.of(), null);
    }

    private static void assertSameOrder(SampleHttpTransport.Response canonical, SampleHttpTransport.Response actual,
                                        String scheme) throws java.io.IOException {
        assertEquals(200, actual.statusCode(), scheme);
        assertEquals(JSON.readTree(canonical.body()), JSON.readTree(actual.body()), scheme);
    }

    private static void assertCancellation(Driver driver, String id, String token, String suffix,
                                           int status, String state) throws Exception {
        assertEquals(status, driver.send("POST", "/orders/catalog/orders/" + id + "/cancel" + suffix, null, token).statusCode());
        assertEquals(state, driver.find(id).path("orderState").asText());
    }

    private static void assertPermittedCancellation(Driver driver, String id, String slashOrder,
                                                    DefaultSecurityManager manager) throws Exception {
        Collection<Realm> previous = manager.getRealms();
        manager.setRealm(new CancellationRealm());
        try {
            String token = driver.login("canceller", "sample-password");
            assertEquals(403, driver.send("POST", "/orders/catalog/orders/no-write", Map.of("shopId", 7), token).statusCode());
            assertCancellation(driver, id, token, "", 200, "CANCELLED");
            assertCancellation(driver, slashOrder, token, "/", 200, "CANCELLED");
            driver.logout(token);
        } finally {
            manager.setRealms(previous);
        }
    }

    @Test
    void unmatchedRoutesKeepNativeHttpStatus() throws Exception {
        try (EmbeddedServer server = start(false)) {
            Driver driver = new Driver(server.getPort());
            String order = "/orders/catalog/orders/parity-probe";
            assertAll("the router owns unmatched requests",
                    () -> assertEquals(404, driver.send("GET", order + "/no-route", null, null).statusCode()),
                    () -> assertEquals(405, driver.send("GET", order + "/payment", null, null).statusCode()));
        }
    }

    @Test
    void authorizationPrecedesRequiredPaymentParameter() throws Exception {
        try (EmbeddedServer server = start(false)) {
            Driver driver = new Driver(server.getPort());
            String payment = "/orders/catalog/orders/parity-probe/payment";
            int anonymous = driver.send("POST", payment, null, null).statusCode();
            String readerToken = driver.login("reader", "sample-password");
            assertEquals("reader", driver.authenticatedPrincipal(readerToken));
            int reader = driver.send("POST", payment, null, readerToken).statusCode();
            String writerToken = driver.login("alice", "sample-password");
            assertEquals("alice", driver.authenticatedPrincipal(writerToken));
            int writer = driver.send("POST", payment, null, writerToken).statusCode();
            assertAll("payment with no key",
                    () -> assertEquals(401, anonymous, "anonymous must be challenged before binding"),
                    () -> assertEquals(403, reader, "read-only user must be denied before binding"),
                    () -> assertEquals(400, writer, "authorized writer reaches required parameter validation"));
        }
    }

    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        try (EmbeddedServer server = start(false)) {
            Driver driver = new Driver(server.getPort());
            String writerToken = driver.login("alice", "sample-password");
            assertEquals("alice", driver.authenticatedPrincipal(writerToken));
            String id = driver.create("bearer-scheme");
            String token = driver.login("reader", "sample-password");
            assertEquals("reader", driver.authenticatedPrincipal(token));
            String url = "http://127.0.0.1:" + server.getPort() + "/orders/catalog/orders/" + id;
            SampleHttpTransport.Response canonical = readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer " + token));
            assertEquals(200, canonical.statusCode());
            assertEquals(id, JSON.readTree(canonical.body()).path("orderNo").asText());
            assertAll("authorization schemes for the same order and token",
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "bearer " + token)), "bearer"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "bEaReR " + token)), "bEaReR"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer")).statusCode(),
                            "Bearer without token"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer    ")).statusCode(),
                            "Bearer with only separator spaces"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer   invalid-token")).statusCode(),
                            "invalid Bearer token"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of()).statusCode(), "without credentials"));
        }
    }

    @Test
    void nativeLoginAndOrderAuthorization() throws Exception {
        CLEAN.clear();
        THREADS.clear();
        FINISHED.clear();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        Object previousData = io.ddd4j.core.util.SubjectKit.getDataProvider();
        Object previous = cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(org.apache.shiro.SecurityUtils.class, "securityManager"));
        try (EmbeddedServer server = start(true)) {
            assertSame(previous, cn.hutool.core.util.ReflectUtil.getStaticFieldValue(
                    cn.hutool.core.util.ReflectUtil.getField(SecurityUtils.class, "securityManager")));
            NativeAuth auth = server.getApplicationContext().getBean(NativeAuth.class);
            Driver driver = new Driver(server.getPort());
            assertEquals(401, driver.send("GET", "/auth/me", null, null).statusCode());
            assertEquals(401, driver.send("POST", "/auth/login", Map.of("username", "alice", "password", "wrong"), null).statusCode());
            String token = driver.login("alice", "sample-password");
            driver.token = token;
            String id = driver.create("shiro-native-order");
            assertEquals("alice", driver.authenticatedPrincipal(token));
            assertEquals("AWAITING_PAYMENT", driver.find(id).path("orderState").asText());
            driver.logout(token);
            String loggedOut = token;
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal(loggedOut));
            // 同步请求在连接断开后仍按 finally 结束；不声称支持任意 Publisher/线程迁移。
            token = driver.login("alice", "sample-password");
            String slashOrder = driver.create("shiro-permission-slash");
            assertCancellation(driver, id, token, "", 403, "AWAITING_PAYMENT");
            assertCancellation(driver, slashOrder, token, "/", 403, "AWAITING_PAYMENT");
            assertPermittedCancellation(driver, id, slashOrder, (DefaultSecurityManager) auth.manager());
            token = driver.login("alice", "sample-password");
            String observation = java.util.UUID.randomUUID().toString();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                String request = "GET /auth/me HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + token + "\r\nX-Test-Hold: " + observation + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTrue(entered.await(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!FINISHED.contains(observation) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(FINISHED.contains(observation), "the exact disconnected route completed native cleanup");
            assertFalse(CLEAN.contains(false), "native and ddd4j state clean on actual server thread");
            assertFalse(THREADS.isEmpty());
            System.out.println("S04 shiro server lifecycle observations=" + CLEAN.size() + " threads=" + new java.util.TreeSet<>(THREADS));
        }
        assertFalse(CLEAN.contains(false), "all server-thread restoration observations remain clean after Context closes");
        assertSame(previousData, io.ddd4j.core.util.SubjectKit.getDataProvider());
        assertSame(previous, cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(org.apache.shiro.SecurityUtils.class, "securityManager")), "context close restores prior native global owner");
    }

    private static final class CancellationRealm extends SimpleAccountRealm {
        CancellationRealm() {
            SimpleAccount account = new SimpleAccount("canceller", "sample-password", getName());
            account.addStringPermission("order:read");
            account.addStringPermission("order:cancel");
            add(account);
        }
    }

    static class Driver {
        final String base;
        final HttpClient client = HttpClient.newHttpClient();
        final String tenantId;
        String token;

        Driver(int port) {
            base = "http://127.0.0.1:" + port;
            tenantId = java.util.UUID.randomUUID().toString().substring(0, 20);
        }

        HttpResponse<String> send(String method, String path, Object body, String bearer) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenantId);
            if (Objects.nonNull(bearer)) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        public <T> T request(String method, String path, Object body, Class<T> type) {
            try {
                var response = send(method, path, body, token);
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new SecurityException(response.body());
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
                }
                if (type == String.class) {
                    return type.cast(response.body().startsWith("\"") ? JSON.readValue(response.body(), String.class) : response.body());
                }
                return JSON.readValue(response.body(), type);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(exception);
            }
        }

        public String login(String user, String password) {
            token = null;
            token = request("POST", "/auth/login", Map.of("username", user, "password", password), com.fasterxml.jackson.databind.JsonNode.class).path("token").asText();
            return token;
        }

        public String authenticatedPrincipal(String token) {
            this.token = token;
            return request("GET", "/auth/me", null, com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText();
        }

        String create(String orderNo) throws Exception {
            var goods = send("POST", "/orders/catalog/goods/" + orderNo,
                    Map.of("spuName", "Demo", "skuCode", orderNo, "skuName", "Standard", "salePriceCent", 1250, "shopId", 7), token);
            assertEquals(201, goods.statusCode(), goods.body());
            var created = send("POST", "/orders/catalog/orders/" + orderNo,
                    Map.of("shopId", 7, "items", List.of(Map.of("skuCode", orderNo, "quantity", 1))), token);
            assertEquals(201, created.statusCode(), created.body());
            return orderNo;
        }

        com.fasterxml.jackson.databind.JsonNode find(String orderNo) {
            return request("GET", "/orders/catalog/orders/" + orderNo, null, com.fasterxml.jackson.databind.JsonNode.class);
        }

        public void logout(String token) {
            this.token = token;
            request("POST", "/auth/logout", null, com.fasterxml.jackson.databind.JsonNode.class);
        }

        public Optional<String> contextPrincipal() {
            return CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty();
        }
    }

    @ServerFilter("/**")
    @Order(-200)
    @Requires(property = "sample.test.observer", value = "true")
    static class OuterObserver {
        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next, io.micronaut.core.propagation.MutablePropagatedContext propagated) {
            String observation = request.getHeaders().get("X-Test-Hold");
            propagated.add(new io.micronaut.core.propagation.ThreadPropagatedContextElement<Object>() {
                @Override
                public Object updateThreadContext() {
                    return nativeState();
                }

                @Override
                public void restoreThreadContext(Object previous) {
                    CLEAN.add(previous == nativeState());
                    THREADS.add("restored:" + Thread.currentThread().getName());
                }
            });
            try {
                return next.request(request).proceed();
            } finally {
                CLEAN.add(!nativePresent() && Objects.isNull(org.apache.shiro.util.ThreadContext.getSecurityManager())
                        && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
                THREADS.add(Thread.currentThread().getName());
                if (Objects.nonNull(observation)) {
                    FINISHED.add(observation);
                }
            }
        }
    }

    @ServerFilter("/**")
    @Order(-50)
    @Requires(property = "sample.test.observer", value = "true")
    static class InnerObserver {
        private final NativeAuth auth;

        InnerObserver(NativeAuth auth) {
            this.auth = auth;
        }

        @RequestFilter
        MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next) throws InterruptedException {
            assertTrue(nativePresent(), "actual native context exists inside filter scope");
            assertSame(auth.manager(), SecurityUtils.getSecurityManager());
            request.setAttribute("observed-thread", Thread.currentThread().getName());
            if (request.getHeaders().contains("X-Test-Hold")) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return next.proceed();
        }
    }
}
