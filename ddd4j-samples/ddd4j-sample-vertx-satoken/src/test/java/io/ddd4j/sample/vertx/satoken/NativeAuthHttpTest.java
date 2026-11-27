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
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 用例查询或写入数据时使用的租户标识。
     */
    static final String TENANT = "vertx-auth";
    /**
     * 用于区分订单路由、消息主题或投影事件流的测试名称。
     */
    static final String ORDERS = "/orders/catalog/orders/";
    /**
     * 商品测试所使用的路由或业务编码。
     */
    static final String GOODS = """
            {"spuName":"Book","skuCode":"S1","skuName":"Edition","shopId":7,"salePriceCent":500}
            """;
    /**
     * 测试订单的固定标识，用于组织和查询测试数据。
     */
    static final String ORDER = """
            {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
            """;
    /**
     * 发送与接收测试请求的客户端。
     */
    private final HttpClient client = HttpClient.newHttpClient();
    /**
     * 按请求或业务标识记录完成和上下文清理结果。
     */
    private final Map<String, Boolean> completions = new ConcurrentHashMap<>();
    /**
     * 本地测试服务的基础地址。
     */
    private String base;

    /**
     * 通过 <code>CatalogRuntime.start()</code> 取得当前测试需要的结果。
     *
     * @param db 传给 <code>CatalogRuntime.start()</code> 的 <code>MySQLContainer&lt;?&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(), db.getJdbcUrl(), db.getUsername(), db.getPassword()))</code>
     */
    static CatalogEndpoints catalog(MySQLContainer<?> db) {
        return CatalogRuntime.start(new UnpooledDataSource(db.getDriverClassName(), db.getJdbcUrl(),
                db.getUsername(), db.getPassword()));
    }

    /**
     * 读取 <code>catalog</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>catalog</code>
     */
    private static CatalogEndpoints seedCatalog() {
        var catalog = catalog(MYSQL);
        int status = catalog.createGoods(TENANT, "BOOK", GOODS).getStatus();
        assertTrue(status == 201 || status == 409);
        return catalog;
    }

    /**
     * 调用 <code>Proxy.newProxyInstance()</code>、<code>CatalogEndpoints.class.getClassLoader()</code> 执行当前夹具操作。
     *
     * @param observer 接收测试状态或清理结果的观察回调
     * @return 提供给后续契约断言的 <code>CatalogEndpoints</code> 结果
     */
    private static CatalogEndpoints observing(InvocationHandler observer) {
        return (CatalogEndpoints) Proxy.newProxyInstance(CatalogEndpoints.class.getClassLoader(),
                new Class<?>[]{CatalogEndpoints.class}, observer);
    }

    /**
     * 通过 <code>requestWithHeaders()</code> 取得当前测试需要的结果。
     *
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param token  从测试登录获得的会话凭据
     * @param body   当前请求或响应中要读取的正文
     * @param id     当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>requestWithHeaders(method, path, credentials, body, id)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private HttpResponse<String> request(String method, String path, String token, String body, String id) throws Exception {
        Map<String, String> credentials = Objects.isNull(token) ? Collections.emptyMap()
                : Collections.singletonMap("Authorization", "Bearer " + token);
        return requestWithHeaders(method, path, credentials, body, id);
    }

    /**
     * 调用 <code>HttpRequest.newBuilder()</code>、<code>URI.create()</code>、<code>Duration.ofSeconds()</code> 执行当前夹具操作。
     *
     * @param method      发给被测服务的 HTTP 方法
     * @param path        测试请求的资源路径或待访问文件路径
     * @param credentials 传给 <code>credentials.forEach()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @param body        当前请求或响应中要读取的正文
     * @param id          当前要查找的测试记录标识
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> requestWithHeaders(String method, String path, Map<String, String> credentials,
                                                    String body, String id) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
                .header("X-Request-Id", id).header("X-Tenant-Id", TENANT).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "");
        credentials.forEach(request::header);
        if (Objects.nonNull(body)) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 创建 <code>new JsonObject</code> 供当前测试路径调用。
     *
     * @param user 传给 <code>request()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>new JsonObject(response.body()).getString("token")</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private String login(String user) throws Exception {
        var response = request("POST", "/auth/login", null, "{\"username\":\"" + user + "\",\"password\":\"sample-password\"}", "login-" + user);
        assertEquals(200, response.statusCode(), response.body());
        return new JsonObject(response.body()).getString("token");
    }

    /**
     * 验证<code>denied.statusCode()</code> 等于 <code>403</code>，<code>catalog.order(TENANT, "denied").getStatus()</code> 等于 <code>404</code>，<code>completions.get(key)</code> 等于 <code>Boolean.TRUE</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>created.statusCode()</code> 等于 <code>201</code>，<code>canonical.statusCode()</code> 等于 <code>200</code>，<code>request("GET", orderPath, null, null, "no-credentials").statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 反射访问被测成员、等待并发操作时失败
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
     * 核对<code>actual.statusCode()</code> 等于 <code>200</code>，<code>principal.get()</code> 等于 <code>"reader"</code>。
     *
     * @param expected    当前用例设定的期望值
     * @param path        测试请求的资源路径或待访问文件路径
     * @param credentials 传给 <code>requestWithHeaders()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @param id          当前要查找的测试记录标识
     * @param principal   传给 <code>principal.set()</code> 的 <code>AtomicReference&lt;String&gt;</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>Thread.currentThread().getName().contains("worker")</code>为真，<code>io.ddd4j.core.util.SubjectKit.getSubject().isAuthenticated()</code>为真，<code>catalog.order(TENANT, "HELD").getStatus()</code> 等于 <code>200</code>。
     *
     * @throws Exception 准备或读取夹具文件、反射访问被测成员、等待并发操作时失败
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
     * 验证被测调用 抛出 <code>java.util.concurrent.ExecutionException.class</code>，<code>io.ddd4j.core.util.SubjectKit.subjectProvider</code> 与…为同一实例 <code>provider</code>，<code>login("alice").length() &gt; 0</code>为真。
     *
     * @throws Exception 等待并发操作时失败
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
