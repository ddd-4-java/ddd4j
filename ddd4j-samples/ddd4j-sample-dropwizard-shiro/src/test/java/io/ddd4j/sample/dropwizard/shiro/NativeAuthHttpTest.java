package io.ddd4j.sample.dropwizard.shiro;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
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
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u7528\u4f8b\u67e5\u8be2\u6216\u5199\u5165\u6570\u636e\u65f6\u4f7f\u7528\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    static final String TENANT = "dropwizard-auth";
    /** \u7528\u4e8e\u533a\u5206\u8ba2\u5355\u8def\u7531\u3001\u6d88\u606f\u4e3b\u9898\u6216\u6295\u5f71\u4e8b\u4ef6\u6d41\u7684\u6d4b\u8bd5\u540d\u79f0\u3002 */
    static final String ORDERS = "/orders/catalog/orders/";
    /** \u5546\u54c1\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684\u8def\u7531\u6216\u4e1a\u52a1\u7f16\u7801\u3002 */
    static final String GOODS = "{\"spuName\":\"Book\",\"skuCode\":\"S1\",\"skuName\":\"Edition\",\"shopId\":7,\"salePriceCent\":500}\n";
    /** \u6d4b\u8bd5\u8ba2\u5355\u7684\u56fa\u5b9a\u6807\u8bc6\uff0c\u7528\u4e8e\u7ec4\u7ec7\u548c\u67e5\u8be2\u6d4b\u8bd5\u6570\u636e\u3002 */
    static final String ORDER = "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n";
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private final HttpClient client = HttpClient.newHttpClient();
    /** \u6309\u8bf7\u6c42\u6216\u4e1a\u52a1\u6807\u8bc6\u8bb0\u5f55\u5b8c\u6210\u548c\u4e0a\u4e0b\u6587\u6e05\u7406\u7ed3\u679c\u3002 */
    private final Map<String, Boolean> completions = new ConcurrentHashMap<>();
    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    private String base;

    /**
     * \u8c03\u7528 <code>CatalogRuntime.start()</code>\u3001<code>MYSQL.getDriverClassName()</code>\u3001<code>MYSQL.getJdbcUrl()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CatalogEndpoints</code> \u7ed3\u679c
     */
    static CatalogEndpoints catalog() {
        return CatalogRuntime.start(new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword()));
    }

    /**
     * \u8bfb\u53d6 <code>catalog</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>catalog</code>
     */
    private static CatalogEndpoints seedCatalog() {
        CatalogEndpoints catalog = catalog();
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
     * 按 <code>start</code> 的契约计算并返回结果。
     *
     * @param orders 传入此操作的 <code>CatalogEndpoints</code> 类型 <code>orders</code>
     * @return 返回表达式 <code>start(orders, 0)</code>
     * @throws Exception 完成 <code>start</code> 操作失败
     */
    private DropwizardTestSupport<AuthConfiguration> start(CatalogEndpoints orders) throws Exception {
        return start(orders, 0);
    }

    /**
     * 按 <code>start</code> 的契约计算并返回结果。
     *
     * @param orders 传入此操作的 <code>CatalogEndpoints</code> 类型 <code>orders</code>
     * @param port 传入此操作的 <code>int</code> 类型 <code>port</code>
     * @return 返回表达式 <code>start(orders, port, new AuthConfiguration())</code>
     * @throws Exception 完成 <code>start</code> 操作失败
     */
    private DropwizardTestSupport<AuthConfiguration> start(CatalogEndpoints orders, int port) throws Exception {
        return start(orders, port, new AuthConfiguration());
    }

    /**
     * \u8bfb\u53d6 <code>support</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param orders \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>CatalogEndpoints</code> \u53c2\u6570 <code>orders</code>
     * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
     * @param config \u5f53\u524d\u7528\u4f8b\u88c5\u914d\u6846\u67b6\u65f6\u4f7f\u7528\u7684\u914d\u7f6e
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>support</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private DropwizardTestSupport<AuthConfiguration> start(CatalogEndpoints orders, int port, AuthConfiguration config) throws Exception {
        io.dropwizard.server.DefaultServerFactory server = (io.dropwizard.server.DefaultServerFactory) config.getServerFactory();
        io.dropwizard.jetty.HttpConnectorFactory applicationConnector = (io.dropwizard.jetty.HttpConnectorFactory) server.getApplicationConnectors().get(0);
        applicationConnector.setBindHost("127.0.0.1");
        applicationConnector.setPort(port);
        io.dropwizard.jetty.HttpConnectorFactory adminConnector = (io.dropwizard.jetty.HttpConnectorFactory) server.getAdminConnectors().get(0);
        adminConnector.setBindHost("127.0.0.1");
        adminConnector.setPort(0);
        io.ddd4j.sample.dropwizard.shiro.DropwizardShiroApplication app = new DropwizardShiroApplication(orders, completions::put);
        DropwizardTestSupport<AuthConfiguration> support = new DropwizardTestSupport<AuthConfiguration>(DropwizardShiroApplication.class, config) {
            @Override
            public io.dropwizard.Application<AuthConfiguration> newApplication() {
                return app;
            }
        };
        support.before();
        return support;
    }

    /**
     * \u901a\u8fc7 <code>requestWithHeaders()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>requestWithHeaders(method, path, Objects.isNull(token) ? Map.of() : Map.of("Authorization", "Bearer " + token), body, id)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private HttpResponse<String> request(String method, String path, String token, String body, String id) throws Exception {
        return requestWithHeaders(method, path, Objects.isNull(token) ? ImmutableMap.of()
                : ImmutableMap.of("Authorization", "Bearer " + token), body, id);
    }

    /**
     * \u901a\u8fc7 <code>requestWithHeaders()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param credentials \u4f20\u7ed9 <code>requestWithHeaders()</code> \u7684 <code>Map&lt;String, String&gt;</code> \u8f93\u5165
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>requestWithHeaders(client, method, path, credentials, body, id)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private HttpResponse<String> requestWithHeaders(String method, String path, Map<String, String> credentials,
                                                    String body, String id) throws Exception {
        return requestWithHeaders(client, method, path, credentials, body, id);
    }

    /**
     * \u901a\u8fc7 <code>requestWithHeaders()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param credentials \u4f20\u7ed9 <code>requestWithHeaders()</code> \u7684 <code>Map&lt;String, String&gt;</code> \u8f93\u5165
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>requestWithHeaders(isolatedClient, method, path, credentials, body, id)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private HttpResponse<String> requestOnFreshConnection(String method, String path, Map<String, String> credentials,
                                                          String body, String id) throws Exception {
        // Jetty 的连接级头缓存可能复用同一令牌此前请求的方案写法。
        HttpClient isolatedClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return requestWithHeaders(isolatedClient, method, path, credentials, body, id);
    }

    /**
     * \u8c03\u7528 <code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code>\u3001<code>Duration.ofSeconds()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param transport \u4f20\u7ed9 <code>transport.send()</code> \u7684 <code>HttpClient</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param credentials \u4f20\u7ed9 <code>credentials.forEach()</code> \u7684 <code>Map&lt;String, String&gt;</code> \u8f93\u5165
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> requestWithHeaders(HttpClient transport, String method, String path,
                                                    Map<String, String> credentials, String body, String id) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).header("X-Request-Id", id).header("Idempotency-Key", id).header("X-Tenant-Id", TENANT);
        credentials.forEach(request::header);
        if (Objects.nonNull(body)) {
            request.header("Content-Type", "application/json");
        }
        return transport.send(request.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>mapper.readTree()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param user \u4f20\u7ed9 <code>request()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>mapper.readTree(response.body()).path("token").asText()</code>
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private String login(String user) throws Exception {
        HttpResponse<java.lang.String> response = request("POST", "/auth/login", null, "{\"username\":\"" + user + "\",\"password\":\"sample-password\"}", "login-" + user);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).path("token").asText();
    }

    /**
     * \u9a8c\u8bc1<code>denied.statusCode()</code> \u7b49\u4e8e <code>403</code>\uff0c<code>catalog.order(TENANT, "denied").getStatus()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>completions.get(key)</code> \u7b49\u4e8e <code>Boolean.TRUE</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void nativeCredentialsPermissionsLogoutAndBusinessFailure() throws Exception {
        CatalogEndpoints catalog = seedCatalog();
        DropwizardTestSupport<io.ddd4j.sample.dropwizard.shiro.AuthConfiguration> app = start(catalog);
        base = "http://127.0.0.1:" + app.getLocalPort();
        try {
            assertEquals(401, request("POST", "/auth/login", null, "{\"username\":\"alice\",\"password\":\"bad\"}", "bad-login").statusCode());
            assertEquals(401, request("POST", "/auth/login", null, "{\"password\":\"sample-password\"}", "missing-user").statusCode(), "login without username");
            assertEquals(401, request("POST", "/auth/login", null, "{\"username\":\"alice\"}", "missing-password").statusCode(), "login without password");
            assertEquals(401, request("POST", ORDERS + "anonymous", null, "{}", "anonymous").statusCode());
            String reader = login("reader");
            HttpResponse<java.lang.String> denied = request("POST", ORDERS + "denied", reader, ORDER, "denied");
            assertEquals(403, denied.statusCode(), denied.body());
            assertEquals(404, catalog.order(TENANT, "denied").getStatus(), "deny before persistence");
            String token = login("alice");
            HttpResponse<java.lang.String> created = request("POST", ORDERS + "AUTH-1", token, ORDER, "create");
            assertEquals(201, created.statusCode(), created.body());
            String id = mapper.readTree(created.body()).path("orderNo").asText();
            assertEquals(200, request("GET", ORDERS + id, reader, null, "reader-get").statusCode());
            HttpResponse<java.lang.String> error = request("POST", ORDERS + id + "/payment", token, "{\"paidAmountCent\":1}", "business-error");
            assertEquals(409, error.statusCode(), error.body());
            assertEquals(200, request("POST", "/auth/logout", token, null, "logout").statusCode());
            assertEquals(401, request("GET", ORDERS + id, token, null, "expired").statusCode());
            for (String key : ImmutableList.of("bad-login", "missing-user", "missing-password", "anonymous", "denied", "create", "reader-get", "business-error", "logout", "expired")) {
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
     * \u9a8c\u8bc1<code>wrongMethod.getResponseCode()</code> \u7b49\u4e8e <code>405</code>\uff0c<code>mapper.readTree(body).path("code").asInt()</code> \u7b49\u4e8e <code>405</code>\uff0c<code>allow</code>\u975e\u7a7a\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>created.statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>orderId.isEmpty()</code>\u4e3a\u5047\uff0c<code>request("GET", path, writerToken, null, "remaining-writer").statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        CatalogEndpoints catalog = seedCatalog();
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
            assertAll("authorization schemes and logout for the same order and reader",
                    () -> assertSameOrder(path, readerToken, "bearer ", "lowercase-bearer", canonical, principal),
                    () -> assertSameOrder(path, readerToken, "bEaReR ", "mixed-case-bearer", canonical, principal),
                    () -> assertSameOrder(path, readerToken, "Bearer  ", "two-bearer-spaces", canonical, principal),
                    () -> assertSameOrder(path, readerToken, "bEaReR    ", "four-bearer-spaces", canonical, principal),
                    () -> assertEquals(401, requestOnFreshConnection("GET", path, ImmutableMap.of("Authorization", "Bearer    invalid-token"),
                            null, "spaced-invalid-token").statusCode(), "spaced-invalid-token"),
                    () -> assertEquals(401, requestWithHeaders("GET", path, ImmutableMap.of("Authorization", "BearerX " + readerToken),
                            null, "invalid-scheme").statusCode(), "invalid-scheme"),
                    () -> assertEquals(401, requestWithHeaders("GET", path, ImmutableMap.of(), null,
                            "no-bearer").statusCode(), "no-bearer"),
                    () -> assertEquals(200, requestOnFreshConnection("POST", "/auth/logout",
                                    ImmutableMap.of("Authorization", "bEaReR    " + readerToken), null, "spaced-mixed-case-logout").statusCode(),
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
     * \u6838\u5bf9<code>actual.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>principal.get()</code> \u7b49\u4e8e <code>"reader"</code>\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param authorizationPrefix \u4f20\u7ed9 <code>requestOnFreshConnection()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param requestId \u7528\u4e8e\u68c0\u67e5\u8bf7\u6c42\u5934\u53ca\u54cd\u5e94\u5bf9\u5e94\u5173\u7cfb\u7684\u6807\u8bc6
     * @param canonical \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>HttpResponse&lt;String&gt;</code> \u8f93\u5165
     * @param principal \u4f20\u7ed9 <code>principal.set()</code> \u7684 <code>AtomicReference&lt;String&gt;</code> \u8f93\u5165
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private void assertSameOrder(String path, String token, String authorizationPrefix, String requestId,
                                 HttpResponse<String> canonical, AtomicReference<String> principal) throws Exception {
        principal.set(null);
        HttpResponse<String> actual = requestOnFreshConnection("GET", path, ImmutableMap.of("Authorization", authorizationPrefix + token),
                null, requestId);
        assertEquals(200, actual.statusCode(), requestId);
        assertEquals("reader", principal.get(), requestId);
        assertEquals(mapper.readTree(canonical.body()), mapper.readTree(actual.body()), requestId);
    }

    /**
     * \u9a8c\u8bc1<code>Thread.currentThread().getName().contains("dw-")</code>\u4e3a\u771f\uff0c<code>io.ddd4j.core.util.SubjectKit.getSubject().isAuthenticated()</code>\u4e3a\u771f\uff0c<code>catalog.order(TENANT, "HELD").getStatus()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void disconnectedRequestFinishesBeforeNativeOwnerStops() throws Exception {
        CatalogEndpoints catalog = seedCatalog();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CatalogEndpoints service = observing((proxy, method, arguments) -> {
            if ("placeOrder".equals(method.getName())) {
                assertTrue(Thread.currentThread().getName().contains("dw-"));
                assertTrue(SubjectKit.getSubject().isAuthenticated());
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return method.invoke(catalog, arguments);
        });
        DropwizardTestSupport<io.ddd4j.sample.dropwizard.shiro.AuthConfiguration> app = start(service);
        base = "http://127.0.0.1:" + app.getLocalPort();
        try {
            String token = login("alice");
            String body = ORDER;
            try (Socket socket = new Socket("127.0.0.1", app.getLocalPort())) {
                socket.getOutputStream().write(("POST " + ORDERS + "HELD HTTP/1.1\r\nHost: localhost\r\nX-Tenant-Id: " + TENANT + "\r\nAuthorization: Bearer " + token + "\r\nIdempotency-Key: disconnected\r\nX-Request-Id: disconnected\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n\r\n" + body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            }
            java.util.concurrent.CompletableFuture<java.lang.Void> closing = java.util.concurrent.CompletableFuture.runAsync(app::after);
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
     * \u9a8c\u8bc1<code>CacheKit.getCache(name)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\uff0c<code>CacheKit.get(name, "caller-entry")</code> \u7b49\u4e8e <code>"loaded"</code>\u3002
     * @throws Exception \u88c5\u914d\u8fd0\u884c\u6d4b\u8bd5\u6240\u9700\u7684\u7ec4\u4ef6\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>login("alice").isEmpty()</code>\u4e3a\u5047\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousProvider</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>start(catalog(), occupied.getLocalPort())</code> \u629b\u51fa <code>Exception.class</code>\uff0c<code>io.ddd4j.core.util.SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>prior</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void failedBindRestoresPriorSubjectOwner() throws Exception {
        io.ddd4j.core.subject.SubjectProvider prior = SubjectKit.subjectProvider;
        try (java.net.ServerSocket occupied = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> start(catalog(), occupied.getLocalPort()));
            org.junit.jupiter.api.Assertions.assertSame(prior, SubjectKit.subjectProvider);
        }
    }
}
