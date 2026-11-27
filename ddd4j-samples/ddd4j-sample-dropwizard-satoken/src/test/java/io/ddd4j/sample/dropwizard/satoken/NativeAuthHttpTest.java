package io.ddd4j.sample.dropwizard.satoken;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.dropwizard.testing.DropwizardTestSupport;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    static final String TENANT = "dropwizard-auth";
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
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper mapper = new ObjectMapper();
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
     * 调用 <code>CatalogRuntime.start()</code>、<code>MYSQL.getDriverClassName()</code>、<code>MYSQL.getJdbcUrl()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>CatalogEndpoints</code> 结果
     */
    static CatalogEndpoints catalog() {
        return CatalogRuntime.start(new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword()));
    }

    /**
     * 读取 <code>catalog</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>catalog</code>
     */
    private static CatalogEndpoints seedCatalog() {
        var catalog = catalog();
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
     * 通过 <code>Map.of()</code> 取得当前测试需要的结果。
     *
     * @param authorization 传给 <code>Map.of()</code> 的 <code>String</code> 输入
     * @param tokenName     传给 <code>Map.of()</code> 的 <code>String</code> 输入
     * @param namedToken    传给 <code>Map.of()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>Map.of("Authorization", authorization, tokenName, namedToken)</code>
     */
    private static Map<String, String> bothCredentials(String authorization, String tokenName, String namedToken) {
        return Map.of("Authorization", authorization, tokenName, namedToken);
    }

    /**
     * 通过 <code>start()</code> 取得当前测试需要的结果。
     *
     * @param orders 传给 <code>start()</code> 的 <code>CatalogEndpoints</code> 输入
     * @return 当前测试使用的结果，取自 <code>start(orders, 0)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private io.dropwizard.testing.DropwizardTestSupport<AuthConfiguration> start(CatalogEndpoints orders) throws Exception {
        return start(orders, 0);
    }

    /**
     * 通过 <code>start()</code> 取得当前测试需要的结果。
     *
     * @param orders 传给 <code>start()</code> 的 <code>CatalogEndpoints</code> 输入
     * @param port   测试服务监听的本地端口
     * @return 当前测试使用的结果，取自 <code>start(orders, port, new AuthConfiguration())</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private io.dropwizard.testing.DropwizardTestSupport<AuthConfiguration> start(CatalogEndpoints orders, int port) throws Exception {
        return start(orders, port, new AuthConfiguration());
    }

    /**
     * 读取 <code>support</code> 供当前测试使用。
     *
     * @param orders 本次调用使用的 <code>CatalogEndpoints</code> 参数 <code>orders</code>
     * @param port   测试服务监听的本地端口
     * @param config 当前用例装配框架时使用的配置
     * @return 当前测试使用的结果，取自 <code>support</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private DropwizardTestSupport<AuthConfiguration> start(CatalogEndpoints orders, int port, AuthConfiguration config) throws Exception {
        var server = (io.dropwizard.core.server.DefaultServerFactory) config.getServerFactory();
        io.dropwizard.jetty.HttpConnectorFactory applicationConnector = (io.dropwizard.jetty.HttpConnectorFactory) server.getApplicationConnectors().get(0);
        applicationConnector.setBindHost("127.0.0.1");
        applicationConnector.setPort(port);
        io.dropwizard.jetty.HttpConnectorFactory adminConnector = (io.dropwizard.jetty.HttpConnectorFactory) server.getAdminConnectors().get(0);
        adminConnector.setBindHost("127.0.0.1");
        adminConnector.setPort(0);
        var app = new DropwizardSaTokenApplication(orders, completions::put);
        var support = new io.dropwizard.testing.DropwizardTestSupport<AuthConfiguration>(DropwizardSaTokenApplication.class, config) {
            @Override
            public io.dropwizard.core.Application<AuthConfiguration> newApplication() {
                return app;
            }
        };
        support.before();
        return support;
    }

    /**
     * 通过 <code>requestWithHeaders()</code> 取得当前测试需要的结果。
     *
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param token  从测试登录获得的会话凭据
     * @param body   当前请求或响应中要读取的正文
     * @param id     当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>requestWithHeaders(method, path, Objects.isNull(token) ? Map.of() : Map.of("Authorization", "Bearer " + token), body, id)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private HttpResponse<String> request(String method, String path, String token, String body, String id) throws Exception {
        return requestWithHeaders(method, path, Objects.isNull(token) ? Map.of()
                : Map.of("Authorization", "Bearer " + token), body, id);
    }

    /**
     * 通过 <code>requestWithHeaders()</code> 取得当前测试需要的结果。
     *
     * @param method      发给被测服务的 HTTP 方法
     * @param path        测试请求的资源路径或待访问文件路径
     * @param credentials 传给 <code>requestWithHeaders()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @param body        当前请求或响应中要读取的正文
     * @param id          当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>requestWithHeaders(client, method, path, credentials, body, id)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private HttpResponse<String> requestWithHeaders(String method, String path, Map<String, String> credentials,
                                                    String body, String id) throws Exception {
        return requestWithHeaders(client, method, path, credentials, body, id);
    }

    /**
     * 通过 <code>requestWithHeaders()</code> 取得当前测试需要的结果。
     *
     * @param method      发给被测服务的 HTTP 方法
     * @param path        测试请求的资源路径或待访问文件路径
     * @param credentials 传给 <code>requestWithHeaders()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @param body        当前请求或响应中要读取的正文
     * @param id          当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>requestWithHeaders(isolatedClient, method, path, credentials, body, id)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private HttpResponse<String> requestOnFreshConnection(String method, String path, Map<String, String> credentials,
                                                          String body, String id) throws Exception {
        // Jetty 的连接级头缓存可能复用同一令牌此前请求的方案写法。
        HttpClient isolatedClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return requestWithHeaders(isolatedClient, method, path, credentials, body, id);
    }

    /**
     * 调用 <code>HttpRequest.newBuilder()</code>、<code>URI.create()</code>、<code>Duration.ofSeconds()</code> 执行当前夹具操作。
     *
     * @param transport   传给 <code>transport.send()</code> 的 <code>HttpClient</code> 输入
     * @param method      发给被测服务的 HTTP 方法
     * @param path        测试请求的资源路径或待访问文件路径
     * @param credentials 传给 <code>credentials.forEach()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @param body        当前请求或响应中要读取的正文
     * @param id          当前要查找的测试记录标识
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> requestWithHeaders(HttpClient transport, String method, String path,
                                                    Map<String, String> credentials, String body, String id) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).header("X-Request-Id", id).header("Idempotency-Key", id).header("X-Tenant-Id", TENANT);
        credentials.forEach(request::header);
        if (Objects.nonNull(body)) {
            request.header("Content-Type", "application/json");
        }
        return transport.send(request.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>mapper.readTree()</code> 取得当前测试需要的结果。
     *
     * @param user 传给 <code>request()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>mapper.readTree(response.body()).path("token").asText()</code>
     * @throws Exception 处理测试数据的序列化时失败
     */
    private String login(String user) throws Exception {
        var response = request("POST", "/auth/login", null, "{\"username\":\"" + user + "\",\"password\":\"sample-password\"}", "login-" + user);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).path("token").asText();
    }

    /**
     * 验证<code>denied.statusCode()</code> 等于 <code>403</code>，<code>catalog.order(TENANT, "denied").getStatus()</code> 等于 <code>404</code>，<code>completions.get(key)</code> 等于 <code>Boolean.TRUE</code>。
     *
     * @throws Exception 等待并发操作、处理测试数据的序列化时失败
     */
    @Test
    void nativeCredentialsPermissionsLogoutAndBusinessFailure() throws Exception {
        var catalog = seedCatalog();
        var app = start(catalog);
        base = "http://127.0.0.1:" + app.getLocalPort();
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
            String id = mapper.readTree(created.body()).path("orderNo").asText();
            assertEquals(200, request("GET", ORDERS + id, reader, null, "reader-get").statusCode());
            var error = request("POST", ORDERS + id + "/payment", token, "{\"paidAmountCent\":1}", "business-error");
            assertEquals(409, error.statusCode(), error.body());
            assertEquals(200, request("POST", "/auth/logout", token, null, "logout").statusCode());
            assertEquals(401, request("GET", ORDERS + id, token, null, "expired").statusCode());
            for (String key : java.util.List.of("bad-login", "missing-user", "missing-password", "anonymous", "denied", "create", "reader-get", "business-error", "logout", "expired")) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!completions.containsKey(key) && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals(Boolean.TRUE, completions.get(key), "passive completion " + key);
            }
        } finally {
            app.after();
        }
    }

    /**
     * 验证<code>wrongMethod.getResponseCode()</code> 等于 <code>405</code>，<code>mapper.readTree(body).path("code").asInt()</code> 等于 <code>405</code>，<code>allow</code>非空。
     *
     * @throws Exception 准备或读取夹具文件、处理测试数据的序列化时失败
     */
    @Test
    void unsupportedOrderMethodPreservesNativeAllowHeader() throws Exception {
        DropwizardTestSupport<AuthConfiguration> app = start(catalog());
        try {
            base = "http://127.0.0.1:" + app.getLocalPort();
            String order = ORDERS + "route-boundary";
            assertEquals(404, request("GET", order + "/no-route", null, null, "route-not-found").statusCode());
            assertEquals(401, request("POST", order + "/payment", null, null, "route-auth-required").statusCode());
            HttpURLConnection wrongMethod = (HttpURLConnection) new URL(base + order + "/payment").openConnection();
            wrongMethod.setConnectTimeout(5000);
            wrongMethod.setReadTimeout(5000);
            try {
                assertEquals(405, wrongMethod.getResponseCode());
                try (java.io.InputStream body = wrongMethod.getErrorStream()) {
                    assertEquals(405, mapper.readTree(body).path("code").asInt());
                }
                String allow = wrongMethod.getHeaderField("Allow");
                assertNotNull(allow, "Jersey's Allow header must survive JSON error translation");
                Set<String> allowedMethods = Arrays.stream(allow.split(",")).map(String::trim).collect(Collectors.toSet());
                assertEquals(new HashSet<>(Arrays.asList("POST", "OPTIONS")), allowedMethods);
            } finally {
                wrongMethod.disconnect();
            }
        } finally {
            app.after();
        }
    }

    /**
     * 验证<code>created.statusCode()</code> 等于 <code>201</code>，<code>orderId.isEmpty()</code>为假，<code>request("GET", path, writerToken, null, "remaining-writer").statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 反射访问被测成员、处理测试数据的序列化时失败
     */
    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        var catalog = seedCatalog();
        AtomicReference<String> principal = new AtomicReference<>();
        CatalogEndpoints orders = observing((proxy, method, arguments) -> {
            if ("order".equals(method.getName())) {
                principal.set(SubjectKit.getSubject().getPrincipal().getLoginId().toString());
            }
            return method.invoke(catalog, arguments);
        });
        DropwizardTestSupport<AuthConfiguration> app = start(orders);
        base = "http://127.0.0.1:" + app.getLocalPort();
        try {
            String writerToken = login("alice");
            HttpResponse<String> created = request("POST", ORDERS + "BEARER-SCHEME", writerToken, ORDER, "bearer-create");
            assertEquals(201, created.statusCode(), created.body());
            String orderId = mapper.readTree(created.body()).path("orderNo").asText();
            assertFalse(orderId.isEmpty());
            String path = ORDERS + orderId;
            String readerToken = login("reader");
            assertFalse(readerToken.isEmpty());
            HttpResponse<String> canonical = request("GET", path, readerToken, null, "canonical-bearer");
            assertEquals(200, canonical.statusCode(), canonical.body());
            assertEquals("reader", principal.get(), "canonical Bearer principal");
            assertEquals(orderId, mapper.readTree(canonical.body()).path("orderNo").asText());
            String tokenName = StpUtil.getTokenName();
            String order = ORDER;
            assertAll("authorization schemes and logout for the same order and reader",
                    () -> assertSameOrder(path, Map.of("Authorization", "bearer " + readerToken), "lowercase-bearer", canonical, principal),
                    () -> assertSameOrder(path, Map.of("Authorization", "bEaReR " + readerToken), "mixed-case-bearer", canonical, principal),
                    () -> assertSameOrder(path, Map.of("Authorization", "Bearer  " + readerToken), "two-bearer-spaces", canonical, principal),
                    () -> assertSameOrder(path, Map.of("Authorization", "bEaReR    " + readerToken), "four-bearer-spaces", canonical, principal),
                    () -> assertEquals(401, requestOnFreshConnection("GET", path, Map.of("Authorization", "Bearer    invalid-token"),
                            null, "spaced-invalid-token").statusCode(), "spaced-invalid-token"),
                    () -> assertEquals(401, requestWithHeaders("GET", path, Map.of("Authorization", "BearerX " + readerToken),
                            null, "invalid-scheme").statusCode(), "invalid-scheme"),
                    () -> assertEquals(401, requestWithHeaders("GET", path, Map.of(), null,
                            "no-bearer").statusCode(), "no-bearer"),
                    () -> assertEquals(401, requestWithHeaders("GET", path, Map.of(tokenName, readerToken),
                            null, "named-token-only").statusCode(), "named-token-only"),
                    () -> assertEquals(401, requestWithHeaders("GET", path, bothCredentials("BearerX " + readerToken,
                            tokenName, readerToken), null, "invalid-with-named-token").statusCode(), "invalid-with-named-token"),
                    () -> assertEquals(403, requestWithHeaders("POST", ORDERS + "forbidden", bothCredentials("Bearer " + readerToken,
                            tokenName, writerToken), order, "canonical-bearer-precedence").statusCode(), "canonical-bearer-precedence"),
                    () -> assertEquals(403, requestOnFreshConnection("POST", ORDERS + "forbidden", bothCredentials("bEaReR " + readerToken,
                            tokenName, writerToken), order, "mixed-case-bearer-precedence").statusCode(), "mixed-case-bearer-precedence"),
                    () -> assertEquals(403, requestOnFreshConnection("POST", ORDERS + "forbidden", bothCredentials("bEaReR    " + readerToken,
                            tokenName, writerToken), order, "spaced-bearer-precedence").statusCode(), "spaced-bearer-precedence"),
                    () -> assertSameOrder(path, bothCredentials("bEaReR    " + readerToken, tokenName, writerToken),
                            "spaced-bearer-reader-precedence", canonical, principal),
                    () -> assertEquals(200, requestOnFreshConnection("POST", "/auth/logout",
                                    Map.of("Authorization", "bEaReR    " + readerToken), null, "spaced-mixed-case-logout").statusCode(),
                            "spaced-mixed-case-logout"),
                    () -> assertEquals(401, request("GET", path, readerToken, null,
                            "revoked-reader").statusCode(), "revoked-reader"),
                    () -> assertEquals(200, request("GET", path, writerToken, null,
                            "remaining-writer").statusCode(), "remaining-writer"));
        } finally {
            app.after();
        }
    }

    /**
     * 核对<code>actual.statusCode()</code> 等于 <code>200</code>，<code>principal.get()</code> 等于 <code>"reader"</code>。
     *
     * @param path        测试请求的资源路径或待访问文件路径
     * @param credentials 传给 <code>requestOnFreshConnection()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @param requestId   用于检查请求头及响应对应关系的标识
     * @param canonical   传给 <code>assertEquals()</code> 的 <code>HttpResponse&lt;String&gt;</code> 输入
     * @param principal   传给 <code>principal.set()</code> 的 <code>AtomicReference&lt;String&gt;</code> 输入
     * @throws Exception 处理测试数据的序列化时失败
     */
    private void assertSameOrder(String path, Map<String, String> credentials, String requestId,
                                 HttpResponse<String> canonical, AtomicReference<String> principal) throws Exception {
        principal.set(null);
        HttpResponse<String> actual = requestOnFreshConnection("GET", path, credentials,
                null, requestId);
        assertEquals(200, actual.statusCode(), requestId);
        assertEquals("reader", principal.get(), requestId);
        assertEquals(mapper.readTree(canonical.body()), mapper.readTree(actual.body()), requestId);
    }

    /**
     * 验证<code>Thread.currentThread().getName().contains("dw-")</code>为真，<code>SubjectKit.getSubject().isAuthenticated()</code>为真，<code>catalog.order(TENANT, "HELD").getStatus()</code> 等于 <code>200</code>。
     *
     * @throws Exception 准备或读取夹具文件、反射访问被测成员、等待并发操作时失败
     */
    @Test
    void disconnectedRequestFinishesBeforeNativeOwnerStops() throws Exception {
        var catalog = seedCatalog();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = observing((proxy, method, arguments) -> {
            if ("placeOrder".equals(method.getName())) {
                assertTrue(Thread.currentThread().getName().contains("dw-"));
                assertTrue(SubjectKit.getSubject().isAuthenticated());
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return method.invoke(catalog, arguments);
        });
        var app = start(service);
        base = "http://127.0.0.1:" + app.getLocalPort();
        try {
            String token = login("alice");
            String body = ORDER;
            try (var socket = new Socket("127.0.0.1", app.getLocalPort())) {
                socket.getOutputStream().write(("POST " + ORDERS + "HELD HTTP/1.1\r\nHost: localhost\r\nX-Tenant-Id: " + TENANT + "\r\nAuthorization: Bearer " + token + "\r\nIdempotency-Key: disconnected\r\nX-Request-Id: disconnected\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n\r\n" + body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            }
            var closing = java.util.concurrent.CompletableFuture.runAsync(app::after);
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class, () -> closing.get(300, TimeUnit.MILLISECONDS));
            assertFalse(completions.containsKey("disconnected"));
            release.countDown();
            closing.get(15, TimeUnit.SECONDS);
            assertEquals(Boolean.TRUE, completions.get("disconnected"));
            assertEquals(200, catalog.order(TENANT, "HELD").getStatus(), "disconnect does not roll back accepted writes");
        } finally {
            release.countDown();
            app.after();
        }
    }

    /**
     * 验证<code>CacheKit.getCache(name)</code> 与…为同一实例 <code>caller</code>，<code>CacheKit.get(name, "caller-entry")</code> 等于 <code>"loaded"</code>。
     *
     * @throws Exception 装配运行测试所需的组件时失败
     */
    @Test
    void shutdownPreservesCallerReplacementAndLoadingCache() throws Exception {
        AuthConfiguration config = new AuthConfiguration();
        DropwizardTestSupport<AuthConfiguration> app = start(catalog(), 0, config);
        String name = config.getDdd4jWeb().getIdempotencyCacheName();
        Cache<String, Object> caller;
        try {
            CacheKit.build(name, 300);
            caller = CacheKit.getCache(name);
            CacheKit.put(name, "caller-entry", "retained");
            CacheKit.buildWithLoader(name, key -> "loaded", builder -> builder);
        } finally {
            app.after();
        }
        try {
            assertSame(caller, CacheKit.getCache(name));
            assertEquals("retained", CacheKit.get(name, "caller-entry"));
            CacheKit.unregisterLocalIfOwned(name, caller);
            assertEquals("loaded", CacheKit.get(name, "caller-entry"));
        } finally {
            CacheKit.unregister(name);
        }
    }

    /**
     * 验证<code>login("alice").isEmpty()</code>为假，<code>SubjectKit.subjectProvider</code> 与…为同一实例 <code>previousProvider</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void disabledCacheStillRunsAndStopsNativeAuth() throws Exception {
        AuthConfiguration config = new AuthConfiguration();
        config.getDdd4jWeb().setIdempotencyEnabled(false);
        Object previousProvider = SubjectKit.subjectProvider;
        DropwizardTestSupport<AuthConfiguration> app = start(catalog(), 0, config);
        try {
            base = "http://127.0.0.1:" + app.getLocalPort();
            assertFalse(login("alice").isEmpty());
            assertNull(CacheKit.getCache(config.getDdd4jWeb().getIdempotencyCacheName()));
        } finally {
            app.after();
        }
        assertSame(previousProvider, SubjectKit.subjectProvider);
    }

    /**
     * 验证<code>start(catalog(), occupied.getLocalPort())</code> 抛出 <code>Exception.class</code>，<code>io.ddd4j.core.util.SubjectKit.subjectProvider</code> 与…为同一实例 <code>prior</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void failedBindRestoresPriorSubjectOwner() throws Exception {
        var prior = io.ddd4j.core.util.SubjectKit.subjectProvider;
        try (var occupied = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> start(catalog(), occupied.getLocalPort()));
            org.junit.jupiter.api.Assertions.assertSame(prior, io.ddd4j.core.util.SubjectKit.subjectProvider);
        }
    }
}
