package io.ddd4j.sample.micronaut.satoken;

import cn.dev33.satoken.stp.StpUtil;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.Socket;
import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
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
        return ApplicationContext.run(EmbeddedServer.class, ImmutableMap.of("micronaut.server.port", -1,
                "sample.test.observer", observe, "catalog.jdbc.url", MYSQL.getJdbcUrl(),
                "catalog.db.user", MYSQL.getUsername(), "catalog.db.password", MYSQL.getPassword(),
                "ddd4j.web.idempotency-enabled", false));
    }

    static boolean nativePresent() {
        return SaManager.getSaTokenContext().isValid();
    }

    static Object nativeState() {
        cn.dev33.satoken.context.SaTokenContext context = SaManager.getSaTokenContext();
        return context.isValid() ? context.getModelBox().getRequest() : null;
    }

    private static SampleHttpTransport.Response readOrder(String url, String tenantId, Map<String, String> headers)
            throws java.io.IOException {
        java.util.HashMap<java.lang.String, java.lang.String> requestHeaders = new java.util.HashMap<>(headers);
        requestHeaders.put("X-Tenant-Id", tenantId);
        return SampleHttpTransport.request(url, "GET", requestHeaders, ImmutableMap.of(), null);
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

    private static void assertPermittedCancellation(Driver driver, String id, String slashOrder) throws Exception {
        StpInterface previous = SaManager.getStpInterface();
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                if ("alice".equals(loginId)) {
                    return Arrays.asList("order:read", "order:cancel");
                }
                return previous.getPermissionList(loginId, loginType);
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return previous.getRoleList(loginId, loginType);
            }
        });
        try {
            String token = driver.login("alice", "sample-password");
            assertEquals(403, driver.send("POST", "/orders/catalog/orders/no-write", ImmutableMap.of("shopId", 7), token).statusCode());
            assertCancellation(driver, id, token, "", 200, "CANCELLED");
            assertCancellation(driver, slashOrder, token, "/", 200, "CANCELLED");
            driver.logout(token);
        } finally {
            SaManager.setStpInterface(previous);
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
            SampleHttpTransport.Response canonical = readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer " + token));
            assertEquals(200, canonical.statusCode());
            assertEquals(id, JSON.readTree(canonical.body()).path("orderNo").asText());
            String tokenName = StpUtil.getTokenName();
            assertAll("authorization schemes for the same order and token",
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "bearer " + token)), "bearer"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "bEaReR " + token)), "bEaReR"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer")).statusCode(),
                            "Bearer without token"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer    ")).statusCode(),
                            "Bearer with only separator spaces"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer   invalid-token")).statusCode(),
                            "invalid Bearer token"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of()).statusCode(), "without credentials"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of(tokenName, token)), "native token header"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "BearerX " + token,
                            tokenName, token)), "unsupported scheme falls back to the native token header"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer",
                            tokenName, token)), "empty scheme falls back to the native token header"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of("Authorization", "Bearer   invalid-token",
                            tokenName, token)).statusCode(), "matched Bearer takes precedence over the native token header"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, ImmutableMap.of("Cookie", tokenName + "=" + token)).statusCode(),
                            "cookie credentials are disabled"));
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
        Object previous = cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(SaManager.class, "saTokenDao"));
        try (EmbeddedServer server = start(true)) {
            Driver driver = new Driver(server.getPort());
            assertEquals(401, driver.send("GET", "/auth/me", null, null).statusCode());
            assertEquals(401, driver.send("POST", "/auth/login", ImmutableMap.of("username", "alice", "password", "wrong"), null).statusCode());
            String token = driver.login("alice", "sample-password");
            driver.token = token;
            String id = driver.create("satoken-native-order");
            assertEquals("alice", driver.authenticatedPrincipal(token));
            assertEquals("AWAITING_PAYMENT", driver.find(id).path("orderState").asText());
            driver.logout(token);
            String loggedOut = token;
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal(loggedOut));
            // 同步请求在连接断开后仍按 finally 结束；不声称支持任意 Publisher/线程迁移。
            token = driver.login("alice", "sample-password");
            String slashOrder = driver.create("satoken-permission-slash");
            assertCancellation(driver, id, token, "", 403, "AWAITING_PAYMENT");
            assertCancellation(driver, slashOrder, token, "/", 403, "AWAITING_PAYMENT");
            assertPermittedCancellation(driver, id, slashOrder);
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
            System.out.println("S04 satoken server lifecycle observations=" + CLEAN.size() + " threads=" + new java.util.TreeSet<>(THREADS));
        }
        assertFalse(CLEAN.contains(false), "all server-thread restoration observations remain clean after Context closes");
        assertSame(previousData, io.ddd4j.core.util.SubjectKit.getDataProvider());
        assertSame(previous, cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(SaManager.class, "saTokenDao")), "context close restores prior native global owner");
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
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenantId);
            if (Objects.nonNull(bearer)) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        public <T> T request(String method, String path, Object body, Class<T> type) {
            try {
                HttpResponse<java.lang.String> response = send(method, path, body, token);
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
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                }
                throw new IllegalStateException(exception);
            }
        }

        public String login(String user, String password) {
            token = null;
            token = request("POST", "/auth/login", ImmutableMap.of("username", user, "password", password), com.fasterxml.jackson.databind.JsonNode.class).path("token").asText();
            return token;
        }

        public String authenticatedPrincipal(String token) {
            this.token = token;
            return request("GET", "/auth/me", null, com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText();
        }

        String create(String orderNo) throws Exception {
            HttpResponse<java.lang.String> goods = send("POST", "/orders/catalog/goods/" + orderNo,
                    ImmutableMap.of("spuName", "Demo", "skuCode", orderNo, "skuName", "Standard", "salePriceCent", 1250, "shopId", 7), token);
            assertEquals(201, goods.statusCode(), goods.body());
            HttpResponse<java.lang.String> created = send("POST", "/orders/catalog/orders/" + orderNo,
                    ImmutableMap.of("shopId", 7, "items", ImmutableList.of(ImmutableMap.of("skuCode", orderNo, "quantity", 1))), token);
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

    @Singleton
    @InterceptorBean(NativeAuthenticated.class)
    @Requires(property = "sample.test.observer", value = "true")
    static class OuterObserver implements MethodInterceptor<Object, Object> {
        @Override
        public int getOrder() {
            return -200;
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> invocation) {
            Object previous = nativeState();
            String observation = ServerRequestContext.currentRequest()
                    .map(request -> request.getHeaders().get("X-Test-Hold")).orElse(null);
            try {
                return invocation.proceed();
            } finally {
                boolean restored = previous == nativeState();
                CLEAN.add(restored && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
                THREADS.add("restored:" + Thread.currentThread().getName());
                THREADS.add(Thread.currentThread().getName());
                if (Objects.nonNull(observation)) {
                    FINISHED.add(observation);
                }
            }
        }
    }

    @Singleton
    @InterceptorBean(NativeAuthenticated.class)
    @Requires(property = "sample.test.observer", value = "true")
    static class InnerObserver implements MethodInterceptor<Object, Object> {
        @Override
        public int getOrder() {
            return 100;
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> invocation) {
            assertTrue(nativePresent(), "actual native context exists inside filter scope");
            if (StpUtil.isLogin()) {
                assertEquals(StpUtil.hasPermission("order:read"),
                        new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider().getSubject().isPermitted("order:read"));
            }
            ServerRequestContext.currentRequest().ifPresent(request -> {
                request.setAttribute("observed-thread", Thread.currentThread().getName());
                if (request.getHeaders().contains("X-Test-Hold")) {
                    entered.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
            });
            return invocation.proceed();
        }
    }
}
