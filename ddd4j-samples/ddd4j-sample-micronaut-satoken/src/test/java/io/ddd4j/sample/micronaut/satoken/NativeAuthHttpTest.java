package io.ddd4j.sample.micronaut.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpInterface;
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
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    /** \u4fdd\u5b58\u5404\u8bf7\u6c42\u9000\u51fa\u540e\u4e0a\u4e0b\u6587\u6e05\u7406\u7ed3\u679c\u7684\u89c2\u6d4b\u961f\u5217\u3002 */
    static final ConcurrentLinkedQueue<Boolean> CLEAN = new ConcurrentLinkedQueue<>();
    /** \u6536\u96c6\u6267\u884c\u8bf7\u6c42\u6216\u56de\u8c03\u7684\u7ebf\u7a0b\u540d\u4ee5\u4fbf\u65ad\u8a00\u7ebf\u7a0b\u5207\u6362\u3002 */
    static final ConcurrentLinkedQueue<String> THREADS = new ConcurrentLinkedQueue<>();
    /** \u7528\u4e8e\u7b49\u5f85\u6216\u89c2\u5bdf\u6d4b\u8bd5\u5de5\u4f5c\u7ed3\u675f\u7684\u540c\u6b65\u72b6\u6001\u3002 */
    static final ConcurrentLinkedQueue<String> FINISHED = new ConcurrentLinkedQueue<>();
    /** \u7b49\u5f85\u88ab\u6d4b\u64cd\u4f5c\u5df2\u8fdb\u5165\u6267\u884c\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
    static volatile CountDownLatch entered = new CountDownLatch(1);
    /** \u7528\u6765\u63a7\u5236\u6d4b\u8bd5\u7ebf\u7a0b\u9000\u51fa\u7b49\u5f85\u6216\u89c2\u5bdf\u64cd\u4f5c\u5b8c\u6210\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
    static volatile CountDownLatch release = new CountDownLatch(1);

    /**
     * \u8c03\u7528 <code>ApplicationContext.run()</code>\u3001<code>MYSQL.getJdbcUrl()</code>\u3001<code>MYSQL.getUsername()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param observe \u4f20\u7ed9 <code>ApplicationContext.run()</code> \u7684 <code>boolean</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>EmbeddedServer</code> \u7ed3\u679c
     */
    static EmbeddedServer start(boolean observe) {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "sample.test.observer", observe, "catalog.jdbc.url", MYSQL.getJdbcUrl(),
                "catalog.db.user", MYSQL.getUsername(), "catalog.db.password", MYSQL.getPassword(),
                "ddd4j.web.idempotency-enabled", false));
    }

    /**
     * \u901a\u8fc7 <code>cn.dev33.satoken.SaManager.getSaTokenContext()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>cn.dev33.satoken.SaManager.getSaTokenContext().isValid()</code>
     */
    static boolean nativePresent() {
        return cn.dev33.satoken.SaManager.getSaTokenContext().isValid();
    }

    /**
     * \u901a\u8fc7 <code>context.isValid()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>context.isValid() ? context.getModelBox().getRequest() : null</code>
     */
    static Object nativeState() {
        var context = cn.dev33.satoken.SaManager.getSaTokenContext();
        return context.isValid() ? context.getModelBox().getRequest() : null;
    }

    /**
     * \u901a\u8fc7 <code>SampleHttpTransport.request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param url \u72ec\u7acb\u6d4b\u8bd5\u670d\u52a1\u63a5\u6536\u8bf7\u6c42\u7684 URL
     * @param tenantId \u6d4b\u8bd5\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6
     * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>SampleHttpTransport.request(url, "GET", requestHeaders, Map.of(), null)</code>
     * @throws java.io.IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private static SampleHttpTransport.Response readOrder(String url, String tenantId, Map<String, String> headers)
            throws java.io.IOException {
        var requestHeaders = new java.util.HashMap<>(headers);
        requestHeaders.put("X-Tenant-Id", tenantId);
        return SampleHttpTransport.request(url, "GET", requestHeaders, Map.of(), null);
    }

    /**
     * \u6838\u5bf9<code>actual.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>JSON.readTree(actual.body())</code> \u7b49\u4e8e <code>JSON.readTree(canonical.body())</code>\u3002
     * @param canonical \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>SampleHttpTransport.Response</code> \u8f93\u5165
     * @param actual \u53d7\u6d4b\u8c03\u7528\u6216\u6d4b\u8bd5\u73af\u5883\u5b9e\u9645\u4ea7\u751f\u7684\u503c
     * @param scheme \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws java.io.IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private static void assertSameOrder(SampleHttpTransport.Response canonical, SampleHttpTransport.Response actual,
                                        String scheme) throws java.io.IOException {
        assertEquals(200, actual.statusCode(), scheme);
        assertEquals(JSON.readTree(canonical.body()), JSON.readTree(actual.body()), scheme);
    }

    /**
     * \u6838\u5bf9<code>driver.find(id).path("orderState").asText()</code> \u7b49\u4e8e <code>state</code>\u3002
     * @param driver \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>Driver</code> \u8f93\u5165
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param suffix \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param state \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static void assertCancellation(Driver driver, String id, String token, String suffix,
                                           int status, String state) throws Exception {
        assertEquals(status, driver.send("POST", "/orders/catalog/orders/" + id + "/cancel" + suffix, null, token).statusCode());
        assertEquals(state, driver.find(id).path("orderState").asText());
    }

    /**
     * \u6838\u5bf9<code>SaManager.getStpInterface()</code>\u3001<code>SaManager.setStpInterface()</code>\u3001<code>driver.login()</code> \u8fd4\u56de\u7684\u6d4b\u8bd5\u7ed3\u679c\u3002
     * @param driver \u4f20\u7ed9 <code>driver.login()</code> \u7684 <code>Driver</code> \u8f93\u5165
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param slashOrder \u4f20\u7ed9 <code>assertCancellation()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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
            assertEquals(403, driver.send("POST", "/orders/catalog/orders/no-write", Map.of("shopId", 7), token).statusCode());
            assertCancellation(driver, id, token, "", 200, "CANCELLED");
            assertCancellation(driver, slashOrder, token, "/", 200, "CANCELLED");
            driver.logout(token);
        } finally {
            SaManager.setStpInterface(previous);
        }
    }

    /**
     * \u9a8c\u8bc1<code>driver.send("GET", order + "/no-route", null, null).statusCode()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>driver.send("GET", order + "/payment", null, null).statusCode()</code> \u7b49\u4e8e <code>405</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>driver.authenticatedPrincipal(readerToken)</code> \u7b49\u4e8e <code>"reader"</code>\uff0c<code>driver.authenticatedPrincipal(writerToken)</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>writer</code> \u7b49\u4e8e <code>400</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>driver.authenticatedPrincipal(writerToken)</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>driver.authenticatedPrincipal(token)</code> \u7b49\u4e8e <code>"reader"</code>\uff0c<code>readOrder(url, driver.tenantId, Map.of("Cookie", tokenName + "=" + token)).statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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
            String tokenName = cn.dev33.satoken.stp.StpUtil.getTokenName();
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
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of()).statusCode(), "without credentials"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of(tokenName, token)), "native token header"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "BearerX " + token,
                            tokenName, token)), "unsupported scheme falls back to the native token header"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer",
                            tokenName, token)), "empty scheme falls back to the native token header"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer   invalid-token",
                            tokenName, token)).statusCode(), "matched Bearer takes precedence over the native token header"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Cookie", tokenName + "=" + token)).statusCode(),
                            "cookie credentials are disabled"));
        }
    }

    /**
     * \u9a8c\u8bc1<code>driver.send("GET", "/auth/me", null, null).statusCode()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>driver.authenticatedPrincipal(token)</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>io.ddd4j.core.util.SubjectKit.getDataProvider()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousData</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void nativeLoginAndOrderAuthorization() throws Exception {
        CLEAN.clear();
        THREADS.clear();
        FINISHED.clear();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        Object previousData = io.ddd4j.core.util.SubjectKit.getDataProvider();
        Object previous = cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(cn.dev33.satoken.SaManager.class, "saTokenDao"));
        try (EmbeddedServer server = start(true)) {
            Driver driver = new Driver(server.getPort());
            assertEquals(401, driver.send("GET", "/auth/me", null, null).statusCode());
            assertEquals(401, driver.send("POST", "/auth/login", Map.of("username", "alice", "password", "wrong"), null).statusCode());
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
        assertSame(previous, cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(cn.dev33.satoken.SaManager.class, "saTokenDao")), "context close restores prior native global owner");
    }

    /** \u5c06 <code>base</code>\u3001<code>client</code>\u3001<code>tenantId</code>\u3001<code>token</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static class Driver {
        /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
        final String base;
        /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
        final HttpClient client = HttpClient.newHttpClient();
        /** \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002 */
        final String tenantId;
        /** \u8bf7\u6c42\u8ba4\u8bc1\u6d4b\u8bd5\u4e2d\u4f7f\u7528\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002 */
        String token;

        /**
         * \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>Driver</code> \u5b9e\u4f8b\u3002
         * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
         */
        Driver(int port) {
            base = "http://127.0.0.1:" + port;
            tenantId = java.util.UUID.randomUUID().toString().substring(0, 20);
        }

        /**
         * \u8c03\u7528 <code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code>\u3001<code>builder.header()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @param bearer \u4f20\u7ed9 <code>Objects.nonNull()</code> \u7684 <code>String</code> \u8f93\u5165
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
         * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
         */
        HttpResponse<String> send(String method, String path, Object body, String bearer) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenantId);
            if (Objects.nonNull(bearer)) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        /**
         * \u6309 <code>(response.statusCode() == 401 || response.statusCode() == 403)</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>type.cast(response.body().startsWith("\"") ? JSON.readValue(response.body(), String.class) : response.body())</code>\u3001<code>JSON.readValue(response.body(), type)</code>
         */
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

        /**
         * \u8bfb\u53d6\u8bf7\u6c42\u8ba4\u8bc1\u6d4b\u8bd5\u4e2d\u4f7f\u7528\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002
         * @param user \u4f20\u7ed9 <code>request()</code> \u7684 <code>String</code> \u8f93\u5165
         * @param password \u6d4b\u8bd5\u8d26\u53f7\u7684\u53e3\u4ee4
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>token</code>
         */
        public String login(String user, String password) {
            token = null;
            token = request("POST", "/auth/login", Map.of("username", user, "password", password), com.fasterxml.jackson.databind.JsonNode.class).path("token").asText();
            return token;
        }

        /**
         * \u901a\u8fc7 <code>request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>request("GET", "/auth/me", null, com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText()</code>
         */
        public String authenticatedPrincipal(String token) {
            this.token = token;
            return request("GET", "/auth/me", null, com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText();
        }

        /**
         * \u8bfb\u53d6 <code>orderNo</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param orderNo \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u79df\u6237\u5185\u8ba2\u5355\u53f7
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>orderNo</code>
         * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
         */
        String create(String orderNo) throws Exception {
            var goods = send("POST", "/orders/catalog/goods/" + orderNo,
                    Map.of("spuName", "Demo", "skuCode", orderNo, "skuName", "Standard", "salePriceCent", 1250, "shopId", 7), token);
            assertEquals(201, goods.statusCode(), goods.body());
            var created = send("POST", "/orders/catalog/orders/" + orderNo,
                    Map.of("shopId", 7, "items", List.of(Map.of("skuCode", orderNo, "quantity", 1))), token);
            assertEquals(201, created.statusCode(), created.body());
            return orderNo;
        }

        /**
         * \u901a\u8fc7 <code>request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param orderNo \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u79df\u6237\u5185\u8ba2\u5355\u53f7
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>request("GET", "/orders/catalog/orders/" + orderNo, null, com.fasterxml.jackson.databind.JsonNode.class)</code>
         */
        com.fasterxml.jackson.databind.JsonNode find(String orderNo) {
            return request("GET", "/orders/catalog/orders/" + orderNo, null, com.fasterxml.jackson.databind.JsonNode.class);
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.token = token</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         */
        public void logout(String token) {
            this.token = token;
            request("POST", "/auth/logout", null, com.fasterxml.jackson.databind.JsonNode.class);
        }

        /**
         * \u901a\u8fc7 <code>CLEAN.contains()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty()</code>
         */
        public Optional<String> contextPrincipal() {
            return CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty();
        }
    }

    /** \u63d0\u4f9b <code>observe()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    @ServerFilter("/**")
    @Order(-200)
    @Requires(property = "sample.test.observer", value = "true")
    static class OuterObserver {
        /**
         * \u901a\u8fc7 <code>next.request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         * @param next \u4f20\u7ed9 <code>next.request()</code> \u7684 <code>FilterContinuation&lt;MutableHttpResponse&lt;?&gt;&gt;</code> \u8f93\u5165
         * @param propagated \u4f20\u7ed9 <code>propagated.add()</code> \u7684 <code>io.micronaut.core.propagation.MutablePropagatedContext</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>next.request(request).proceed()</code>
         */
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
                CLEAN.add(!nativePresent() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
                THREADS.add(Thread.currentThread().getName());
                if (Objects.nonNull(observation)) {
                    FINISHED.add(observation);
                }
            }
        }
    }

    /** \u63d0\u4f9b <code>observe()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    @ServerFilter("/**")
    @Order(-50)
    @Requires(property = "sample.test.observer", value = "true")
    static class InnerObserver {
        /**
         * \u901a\u8fc7 <code>next.proceed()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         * @param next \u4f20\u7ed9 <code>next.proceed()</code> \u7684 <code>FilterContinuation&lt;MutableHttpResponse&lt;?&gt;&gt;</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>next.proceed()</code>
         * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
         */
        @RequestFilter
        MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next) throws InterruptedException {
            assertTrue(nativePresent(), "actual native context exists inside filter scope");
            if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
                assertEquals(cn.dev33.satoken.stp.StpUtil.hasPermission("order:read"), new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider().getSubject().isPermitted("order:read"), "native and DDD permission source agree");
            }
            request.setAttribute("observed-thread", Thread.currentThread().getName());
            if (request.getHeaders().contains("X-Test-Hold")) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return next.proceed();
        }
    }
}
