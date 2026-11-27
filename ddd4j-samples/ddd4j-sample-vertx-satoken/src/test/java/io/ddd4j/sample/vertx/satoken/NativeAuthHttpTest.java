package io.ddd4j.sample.vertx.satoken;

import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.vertx.core.json.JsonObject;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实际启动原生 HTTP/凭证/权限/退出及工作线程清理；不创建 Subject 替身。
 */
@Testcontainers
class NativeAuthHttpTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u7528\u4f8b\u67e5\u8be2\u6216\u5199\u5165\u6570\u636e\u65f6\u4f7f\u7528\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    static final String TENANT = "vertx-auth";
    /** \u7528\u4e8e\u533a\u5206\u8ba2\u5355\u8def\u7531\u3001\u6d88\u606f\u4e3b\u9898\u6216\u6295\u5f71\u4e8b\u4ef6\u6d41\u7684\u6d4b\u8bd5\u540d\u79f0\u3002 */
    static final String ORDERS = "/orders/catalog/orders/";
    /** \u5546\u54c1\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684\u8def\u7531\u6216\u4e1a\u52a1\u7f16\u7801\u3002 */
    static final String GOODS = """
            {"spuName":"Book","skuCode":"S1","skuName":"Edition","shopId":7,"salePriceCent":500}
            """;
    /** \u6d4b\u8bd5\u8ba2\u5355\u7684\u56fa\u5b9a\u6807\u8bc6\uff0c\u7528\u4e8e\u7ec4\u7ec7\u548c\u67e5\u8be2\u6d4b\u8bd5\u6570\u636e\u3002 */
    static final String ORDER = """
            {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
            """;
    /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private final HttpClient client = HttpClient.newHttpClient();
    /** \u6309\u8bf7\u6c42\u6216\u4e1a\u52a1\u6807\u8bc6\u8bb0\u5f55\u5b8c\u6210\u548c\u4e0a\u4e0b\u6587\u6e05\u7406\u7ed3\u679c\u3002 */
    private final Map<String, Boolean> completions = new ConcurrentHashMap<>();
    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    private String base;

    /**
     * \u901a\u8fc7 <code>CatalogRuntime.start()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param db \u4f20\u7ed9 <code>CatalogRuntime.start()</code> \u7684 <code>MySQLContainer&lt;?&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(), db.getJdbcUrl(), db.getUsername(), db.getPassword()))</code>
     */
    static CatalogEndpoints catalog(MySQLContainer<?> db) {
        return CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(), db.getJdbcUrl(),
                db.getUsername(), db.getPassword()));
    }

    /**
     * \u8bfb\u53d6 <code>catalog</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>catalog</code>
     */
    private static CatalogEndpoints seedCatalog() {
        var catalog = catalog(MYSQL);
        int status = catalog.createGoods(TENANT, "BOOK", GOODS).getStatus();
        assertTrue(status == 201 || status == 409);
        return catalog;
    }

    /**
     * \u8c03\u7528 <code>Proxy.newProxyInstance()</code>\u3001<code>CatalogEndpoints.class.getClassLoader()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param observer \u63a5\u6536\u6d4b\u8bd5\u72b6\u6001\u6216\u6e05\u7406\u7ed3\u679c\u7684\u89c2\u5bdf\u56de\u8c03
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CatalogEndpoints</code> \u7ed3\u679c
     */
    private static CatalogEndpoints observing(InvocationHandler observer) {
        return (CatalogEndpoints) Proxy.newProxyInstance(CatalogEndpoints.class.getClassLoader(),
                new Class<?>[]{CatalogEndpoints.class}, observer);
    }

    /**
     * \u901a\u8fc7 <code>requestWithHeaders()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>requestWithHeaders(method, path, credentials, body, id)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private HttpResponse<String> request(String method, String path, String token, String body, String id) throws Exception {
        Map<String, String> credentials = Objects.isNull(token) ? Collections.emptyMap()
                : Collections.singletonMap("Authorization", "Bearer " + token);
        return requestWithHeaders(method, path, credentials, body, id);
    }

    /**
     * \u8c03\u7528 <code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code>\u3001<code>Duration.ofSeconds()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param credentials \u4f20\u7ed9 <code>credentials.forEach()</code> \u7684 <code>Map&lt;String, String&gt;</code> \u8f93\u5165
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> requestWithHeaders(String method, String path, Map<String, String> credentials,
                                                    String body, String id) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
                .header("X-Request-Id", id).header("X-Tenant-Id", TENANT);
        credentials.forEach(request::header);
        if (Objects.nonNull(body)) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u521b\u5efa <code>new JsonObject</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param user \u4f20\u7ed9 <code>request()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new JsonObject(response.body()).getString("token")</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private String login(String user) throws Exception {
        var response = request("POST", "/auth/login", null, "{\"username\":\"" + user + "\",\"password\":\"sample-password\"}", "login-" + user);
        assertEquals(200, response.statusCode(), response.body());
        return new JsonObject(response.body()).getString("token");
    }

    /**
     * \u9a8c\u8bc1<code>denied.statusCode()</code> \u7b49\u4e8e <code>403</code>\uff0c<code>catalog.order(TENANT, "denied").getStatus()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>completions.get(key)</code> \u7b49\u4e8e <code>Boolean.TRUE</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void nativeCredentialsPermissionsLogoutAndBusinessFailure() throws Exception {
        var catalog = seedCatalog();
        var app = VertxSaTokenApplication.start(0, catalog, completions::put).toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        base = "http://127.0.0.1:" + app.port();
        try {
            assertEquals(401, request("POST", "/auth/login", null, "{\"username\":\"alice\",\"password\":\"bad\"}", "bad-login").statusCode());
            assertEquals(401, request("POST", "/auth/login", null, "{\"password\":\"sample-password\"}", "missing-user").statusCode(), "login without username");
            assertEquals(401, request("POST", "/auth/login", null, "{\"username\":\"alice\"}", "missing-password").statusCode(), "login without password");
            assertEquals(401, request("POST", ORDERS + "anonymous", null, "{}", "anonymous").statusCode());
            String reader = login("reader");
            var denied = request("POST", ORDERS + "denied", reader, ORDER, "denied");
            assertEquals(403, denied.statusCode(), denied.body());
            assertEquals(404, catalog.order(TENANT, "denied").getStatus(), "deny before persistence");
            String token = login("alice");
            var created = request("POST", ORDERS + "AUTH-1", token, ORDER, "create");
            assertEquals(201, created.statusCode(), created.body());
            String id = new JsonObject(created.body()).getString("orderNo");
            assertEquals(200, request("GET", ORDERS + id, reader, null, "reader-get").statusCode());
            var error = request("POST", ORDERS + id + "/payment", token, "{\"paidAmountCent\":1}", "business-error");
            assertTrue(error.statusCode() >= 400, error.body());
            assertEquals(200, request("POST", "/auth/logout", token, null, "logout").statusCode());
            assertEquals(401, request("GET", ORDERS + id, token, null, "expired").statusCode());
            for (String key : java.util.List.of("bad-login", "missing-user", "missing-password", "anonymous", "denied", "create", "reader-get", "business-error", "logout", "expired")) {
                assertEquals(Boolean.TRUE, completions.get(key), "passive completion " + key);
            }
        } finally {
            app.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }

    /**
     * \u9a8c\u8bc1<code>created.statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>canonical.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>request("GET", orderPath, null, null, "no-credentials").statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        var catalog = seedCatalog();
        AtomicReference<String> principal = new AtomicReference<>();
        CatalogEndpoints orders = observing((proxy, method, arguments) -> {
            if ("order".equals(method.getName())) {
                principal.set(ThreadContext.getSubject().getPrincipal().getLoginId().toString());
            }
            return method.invoke(catalog, arguments);
        });
        VertxSaTokenApplication app = VertxSaTokenApplication.start(0, orders, completions::put)
                .toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        base = "http://127.0.0.1:" + app.port();
        try {
            String writer = login("alice");
            HttpResponse<String> created = request("POST", ORDERS + "BEARER-SCHEME", writer, ORDER, "bearer-create");
            assertEquals(201, created.statusCode(), created.body());
            String id = new JsonObject(created.body()).getString("orderNo");
            String orderPath = ORDERS + id;
            String reader = login("reader");
            HttpResponse<String> canonical = request("GET", orderPath, reader, null, "canonical-bearer");
            assertEquals(200, canonical.statusCode(), canonical.body());
            JsonObject expected = new JsonObject(canonical.body());
            assertEquals(id, expected.getString("orderNo"));
            assertEquals("reader", principal.get(), "canonical Bearer principal");
            String tokenName = StpUtil.getTokenName();
            Map<String, String> fallback = new LinkedHashMap<>();
            fallback.put("Authorization", "BearerX " + reader);
            fallback.put(tokenName, reader);
            Map<String, String> competing = new LinkedHashMap<>();
            competing.put("Authorization", "bEaReR " + reader);
            competing.put(tokenName, writer);
            Map<String, String> spacedCompeting = new LinkedHashMap<>(competing);
            spacedCompeting.put("Authorization", "bEaReR    " + reader);
            assertAll("authorization schemes for the same order and reader",
                    () -> assertReaderOrder(expected, orderPath,
                            Collections.singletonMap("Authorization", "bearer " + reader), "lowercase-bearer", principal),
                    () -> assertReaderOrder(expected, orderPath,
                            Collections.singletonMap("Authorization", "bEaReR " + reader), "mixed-case-bearer", principal),
                    () -> assertReaderOrder(expected, orderPath,
                            Collections.singletonMap("Authorization", "Bearer  " + reader), "two-bearer-spaces", principal),
                    () -> assertReaderOrder(expected, orderPath,
                            Collections.singletonMap("Authorization", "bEaReR    " + reader), "four-bearer-spaces", principal),
                    () -> assertEquals(401, requestWithHeaders("GET", orderPath,
                                    Collections.singletonMap("Authorization", "Bearer  invalid-token"), null, "invalid-token").statusCode(),
                            "invalid token after two spaces"),
                    () -> assertEquals(401, requestWithHeaders("GET", orderPath,
                            Collections.singletonMap("Authorization", "BearerX " + reader), null, "wrong-scheme").statusCode(), "BearerX"),
                    () -> assertEquals(401, request("GET", orderPath, null, null, "no-credentials").statusCode(), "anonymous"),
                    () -> assertReaderOrder(expected, orderPath, Collections.singletonMap(tokenName, reader), "native-header", principal),
                    () -> assertReaderOrder(expected, orderPath, fallback, "native-header-fallback", principal),
                    () -> assertReaderOrder(expected, orderPath, competing, "bearer-header-precedence", principal),
                    () -> assertReaderOrder(expected, orderPath, spacedCompeting, "spaced-bearer-header-precedence", principal));
        } finally {
            app.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }

    /**
     * \u6838\u5bf9<code>actual.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>principal.get()</code> \u7b49\u4e8e <code>"reader"</code>\u3002
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param credentials \u4f20\u7ed9 <code>requestWithHeaders()</code> \u7684 <code>Map&lt;String, String&gt;</code> \u8f93\u5165
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param principal \u4f20\u7ed9 <code>principal.set()</code> \u7684 <code>AtomicReference&lt;String&gt;</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private void assertReaderOrder(JsonObject expected, String path, Map<String, String> credentials, String id,
                                   AtomicReference<String> principal) throws Exception {
        principal.set(null);
        HttpResponse<String> actual = requestWithHeaders("GET", path, credentials, null, id);
        assertEquals(200, actual.statusCode(), id);
        assertEquals(expected, new JsonObject(actual.body()), id);
        assertEquals("reader", principal.get(), id);
    }

    /**
     * \u9a8c\u8bc1<code>Thread.currentThread().getName().contains("worker")</code>\u4e3a\u771f\uff0c<code>io.ddd4j.core.util.SubjectKit.getSubject().isAuthenticated()</code>\u4e3a\u771f\uff0c<code>catalog.order(TENANT, "HELD").getStatus()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void disconnectWaitsForActualWorkerCompletionAndCloseDrainsIt() throws Exception {
        var catalog = seedCatalog();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var service = observing((proxy, method, arguments) -> {
            if ("placeOrder".equals(method.getName())) {
                assertTrue(Thread.currentThread().getName().contains("worker"));
                assertTrue(io.ddd4j.core.util.SubjectKit.getSubject().isAuthenticated());
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return method.invoke(catalog, arguments);
        });
        var app = VertxSaTokenApplication.start(0, service, (id, clean) -> {
            completions.put(id, clean);
            if ("disconnected".equals(id)) {
                finished.countDown();
            }
        }).toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        base = "http://127.0.0.1:" + app.port();
        try {
            String token = login("alice");
            String body = ORDER;
            try (var socket = new Socket("127.0.0.1", app.port())) {
                socket.getOutputStream().write(("POST " + ORDERS + "HELD HTTP/1.1\r\nHost: localhost\r\nX-Tenant-Id: " + TENANT + "\r\nAuthorization: Bearer " + token + "\r\nX-Request-Id: disconnected\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n\r\n" + body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(200, request("GET", "/health", null, null, "heartbeat").statusCode(), "event loop remains responsive");
            }
            var close = app.close().toCompletionStage().toCompletableFuture();
            assertFalse(close.isDone());
            assertFalse(completions.containsKey("disconnected"));
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, completions.get("disconnected"));
            close.get(15, TimeUnit.SECONDS);
            assertEquals(200, catalog.order(TENANT, "HELD").getStatus(), "disconnect is not rollback");
        } finally {
            release.countDown();
            app.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>java.util.concurrent.ExecutionException.class</code>\uff0c<code>io.ddd4j.core.util.SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>provider</code>\uff0c<code>login("alice").length() &gt; 0</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void failedListenRestoresRunningApplicationOwner() throws Exception {
        var app = VertxSaTokenApplication.start(0, catalog(MYSQL), (id, clean) -> {
        }).toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        var provider = io.ddd4j.core.util.SubjectKit.subjectProvider;
        try {
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> VertxSaTokenApplication.start(app.port(), catalog(MYSQL), (id, clean) -> {
                    }).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertSame(provider, io.ddd4j.core.util.SubjectKit.subjectProvider);
            base = "http://127.0.0.1:" + app.port();
            assertTrue(login("alice").length() > 0);
        } finally {
            app.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }
}
