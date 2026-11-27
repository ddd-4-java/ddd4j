package io.ddd4j.sample.javalin.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;

import cn.dev33.satoken.SaManager;
import cn.hutool.core.util.ReflectUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实际 main/start 装配、原生认证和共享订单 HTTP 验收。
 */
@Testcontainers
class NativeAuthHttpTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u7528\u4e8e\u533a\u5206\u8ba2\u5355\u8def\u7531\u3001\u6d88\u606f\u4e3b\u9898\u6216\u6295\u5f71\u4e8b\u4ef6\u6d41\u7684\u6d4b\u8bd5\u540d\u79f0\u3002 */
    private static final String ORDERS = "/orders/catalog/orders/";
    /** \u5546\u54c1\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684\u8def\u7531\u6216\u4e1a\u52a1\u7f16\u7801\u3002 */
    private static final String GOODS = "/orders/catalog/goods/";
    /** \u7528\u4f8b\u67e5\u8be2\u6216\u5199\u5165\u6570\u636e\u65f6\u4f7f\u7528\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    private static final String TENANT = "javalin-auth";
    /** \u5f53\u524d\u5408\u540c\u6216\u4ed3\u50a8\u64cd\u4f5c\u6240\u4f7f\u7528\u7684\u8ba2\u5355\u6837\u672c\u3002 */
    private static final Map<String, Object> ORDER = Map.of("shopId", 7,
            "items", List.of(Map.of("skuCode", "SKU-1", "quantity", 2)));

    /**
     * \u8bfb\u53d6 <code>api</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param database \u4f20\u7ed9 <code>CatalogRuntime.start()</code> \u7684 <code>MySQLContainer&lt;?&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>api</code>
     */
    static CatalogEndpoints catalog(MySQLContainer<?> database) {
        var api = CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(),
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        int status = api.createGoods(TENANT, "BOOK", """
                {"spuName":"Book","skuCode":"SKU-1","skuName":"Edition","shopId":7,"salePriceCent":500}
                """).getStatus();
        assertTrue(status == 201 || status == 409);
        return api;
    }

    /**
     * \u901a\u8fc7 <code>start()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>start(port, (ignored)-&gt;{ })</code>
     */
    private static JavalinSaTokenApplication.Application start(int port) {
        return start(port, ignored -> {
        });
    }

    /**
     * \u901a\u8fc7 <code>start()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
     * @param observer \u63a5\u6536\u6d4b\u8bd5\u72b6\u6001\u6216\u6e05\u7406\u7ed3\u679c\u7684\u89c2\u5bdf\u56de\u8c03
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>start(port, observer, (config)-&gt;{ })</code>
     */
    private static JavalinSaTokenApplication.Application start(int port,
                                                               java.util.function.Consumer<Optional<String>> observer) {
        return start(port, observer, config -> {
        });
    }

    /**
     * \u901a\u8fc7 <code>JavalinSaTokenApplication.start()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
     * @param observer \u63a5\u6536\u6d4b\u8bd5\u72b6\u6001\u6216\u6e05\u7406\u7ed3\u679c\u7684\u89c2\u5bdf\u56de\u8c03
     * @param configure \u4f20\u7ed9 <code>JavalinSaTokenApplication.start()</code> \u7684 <code>java.util.function.Consumer&lt;io.javalin.config.JavalinConfig&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>JavalinSaTokenApplication.start(port, observer, configure, catalog(MYSQL))</code>
     */
    private static JavalinSaTokenApplication.Application start(int port,
                                                               java.util.function.Consumer<Optional<String>> observer,
                                                               java.util.function.Consumer<io.javalin.config.JavalinConfig> configure) {
        return JavalinSaTokenApplication.start(port, observer, configure, catalog(MYSQL));
    }

    /**
     * \u6838\u5bf9<code>response.statusCode()</code> \u7b49\u4e8e <code>code</code>\uff0c<code>mapper.readTree(response.body())</code> \u7b49\u4e8e <code>expected</code>\u3002
     * @param base \u4f20\u7ed9 <code>HttpRequest.newBuilder()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static void assertOrderDenial(String base, String path, Map<String, String> headers, String body,
                                          int code, String message, ObjectMapper mapper) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        headers.forEach(request::header);
        if (Objects.isNull(body)) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(code, response.statusCode(), path);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                path + " " + response.body());
        JsonNode expected = mapper.createObjectNode().put("code", code).put("msg", message).putNull("data")
                .put("success", false).put("empty", true).put("ok", false);
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

    /**
     * \u6838\u5bf9<code>response.statusCode()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>mapper.readTree(response.body())</code> \u7b49\u4e8e <code>expected</code>\u3002
     * @param base \u4f20\u7ed9 <code>SampleHttpTransport.request()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
     * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private static void assertMissingOrder(String base, String path, Map<String, String> headers,
                                           ObjectMapper mapper, JsonNode expected) throws IOException {
        SampleHttpTransport.Response response = SampleHttpTransport.request(
                base + path, "GET", headers, Map.of("X-Tenant-Id", TENANT), null);
        assertEquals(404, response.statusCode(), path + " " + response.body());
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

    /**
     * \u9a8c\u8bc1<code>StrKit.hasText(writer)</code>\u4e3a\u771f\uff0c<code>http.request("GET", ORDERS + id, null, headers).path("orderState").asText()</code> \u7b49\u4e8e <code>"AWAITING_PAYMENT"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void orderAuthorizationDenialsUseTheOrderEnvelope() throws Exception {
        try (JavalinSaTokenApplication.Application application = start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            Driver http = new Driver(base, Map.of());
            var credentials = Map.of("username", "admin", "password", "admin");
            String writer = http.request("POST", "/auth/login", credentials, Map.of()).path("token").asText();
            assertTrue(StrKit.hasText(writer), "native authentication returns a real token");
            Map<String, String> headers = Map.of("ddd4j-token", writer);
            String id = http.create("DENIAL-" + UUID.randomUUID(), headers);
            ObjectMapper mapper = new ObjectMapper();
            String anonymousBody = mapper.writeValueAsString(ORDER);

            assertAll(
                    () -> assertOrderDenial(base, ORDERS + "ANONYMOUS", Map.of(), anonymousBody,
                            401, "not login", mapper),
                    () -> assertOrderDenial(base, ORDERS + id + "/cancel", headers, null,
                            403, "required permission is missing", mapper),
                    () -> assertEquals("AWAITING_PAYMENT", http.request("GET", ORDERS + id, null, headers).path("orderState").asText()));
        }
    }

    /**
     * \u9a8c\u8bc1<code>token.isEmpty()</code>\u4e3a\u5047\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void missingSharedOrderReturnsTheOrderDiagnostic() throws IOException {
        try (JavalinSaTokenApplication.Application application = start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            Driver http = new Driver(base, Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"),
                    Map.of()).path("token").asText();
            assertFalse(token.isEmpty(), "native authentication returns a real token");
            Map<String, String> headers = Map.of("ddd4j-token", token);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode expected = mapper.createObjectNode().put("error", "记录不存在或规格在此门店不可售");
            assertMissingOrder(base, ORDERS + "missing-order", headers, mapper, expected);
        }
    }

    /** 验证<code>SaManager.getSaTokenContext()</code> 与…为同一实例 <code>callerContext</code>。 */
    @Test
    void nativeAuthenticationRestoresCallerContext() {
        var previous = SaManager.getSaTokenContext();
        var callerContext = new SaTokenContextForThreadLocal();
        SaManager.setSaTokenContext(callerContext);
        try {
            try (var application = start(0)) {
                var http = new Driver("http://127.0.0.1:" + application.app().port(), Map.of());
                String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of())
                        .path("token").asText();
                assertEquals("10001", http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token))
                        .path("loginId").asText());
            }
            assertSame(callerContext, SaManager.getSaTokenContext());
        } finally {
            SaManager.setSaTokenContext(previous);
        }
    }

    /**
     * \u9a8c\u8bc1<code>completed.poll(10, TimeUnit.SECONDS)</code> \u7b49\u4e8e <code>Optional.empty()</code>\uff0c<code>http.request("POST", ORDERS + id + suffix, null, headers)</code> \u629b\u51fa <code>SecurityException.class</code>\uff0c<code>http.request("GET", ORDERS + id, null, allowedHeaders).path("orderState").asText()</code> \u7b49\u4e8e <code>"CANCELLED"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void cancellationAuthorizationUsesMatchedRouteForBothSlashForms() throws Exception {
        LinkedBlockingQueue<Optional<String>> completed = new LinkedBlockingQueue<>();
        try (var application = start(0, completed::add)) {
            var http = new Driver("http://127.0.0.1:" + application.app().port(), Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            var headers = Map.of("ddd4j-token", token);
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS)); // startup
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS)); // login
            List<String> suffixes = List.of("/cancel", "/cancel/");
            List<String> ids = new ArrayList<>();
            for (String suffix : suffixes) {
                String id = http.create("CANCEL-" + UUID.randomUUID(), headers);
                ids.add(id);
                SecurityException denied = assertThrows(SecurityException.class,
                        () -> http.request("POST", ORDERS + id + suffix, null, headers));
                assertEquals("HTTP 403", denied.getMessage());
                assertEquals("AWAITING_PAYMENT", http.request("GET", ORDERS + id, null, headers).path("orderState").asText());
                for (int request = 0; request < 3; request++) {
                    assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS));
                }
            }
            http.request("POST", "/rbac/admin/permissions", Map.of("permissionId", "P-S02-CANCEL",
                    "permissionCode", "order:cancel", "permissionName", "Cancel order", "module", "order"), headers);
            http.request("POST", "/rbac/admin/roles/R001/permissions", Map.of("permissionIds",
                    List.of("P001", "P002", "P003", "P004", "P005", "P006", "P-S02-CANCEL")), headers);
            for (int request = 0; request < 2; request++) {
                assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS));
            }
            String authorized = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            var allowedHeaders = Map.of("ddd4j-token", authorized);
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS));
            for (int index = 0; index < suffixes.size(); index++) {
                String id = ids.get(index);
                assertEquals("CANCELLED", http.request("POST", ORDERS + id + suffixes.get(index), null, allowedHeaders).path("orderState").asText());
                assertEquals("CANCELLED", http.request("GET", ORDERS + id, null, allowedHeaders).path("orderState").asText());
                for (int request = 0; request < 2; request++) {
                    assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS));
                }
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>value</code>\u975e\u7a7a\uff0c<code>value.isEmpty()</code>\u4e3a\u771f\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void nativeAuthenticationAndBusinessCleanup() throws Exception {
        LinkedBlockingQueue<Optional<String>> completed = new LinkedBlockingQueue<>();
        try (var application = start(0, completed::add)) {
            var http = new Driver("http://127.0.0.1:" + application.app().port(), Map.of());
            SampleContractAssertions.authentication("satoken", new AuthSampleDriver() {
                private String orderId;
                private int pendingCompletions = 1; // startup observation

                @Override
                public String login(String username, String password) {
                    pendingCompletions++;
                    return http.request("POST", "/auth/login", Map.of("username", username, "password", password), Map.of()).path("token").asText();
                }

                @Override
                public String authenticatedPrincipal(String token) {
                    pendingCompletions++;
                    return http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token)).path("loginId").asText();
                }

                @Override
                public String authorizedResource(String token, String permission) {
                    if ("order:cancel".equals(permission)) {
                        pendingCompletions++;
                        http.request("POST", ORDERS + orderId + "/cancel", null, Map.of("ddd4j-token", token));
                        return "unexpected";
                    }
                    pendingCompletions += 2;
                    orderId = http.create("AUTH-" + UUID.randomUUID(), Map.of("ddd4j-token", token));
                    return http.request("GET", ORDERS + orderId, null, Map.of("ddd4j-token", token)).path("orderState").asText();
                }

                @Override
                public void failingRequest(String token) {
                    pendingCompletions++;
                    // 已认证的真实业务失败：金额不匹配被交易领域拒绝。
                    http.request("POST", ORDERS + orderId + "/payment", Map.of("paidAmountCent", 1), Map.of("ddd4j-token", token));
                }

                @Override
                public void logout(String token) {
                    pendingCompletions++;
                    http.request("POST", "/auth/logout", null, Map.of("ddd4j-token", token));
                }

                @Override
                public Optional<String> contextPrincipal() {
                    try {
                        Optional<String> value = null;
                        while (pendingCompletions > 0) {
                            value = completed.poll(10, TimeUnit.SECONDS);
                            assertNotNull(value, "every native request must produce its completion observation");
                            assertTrue(value.isEmpty(), "native or framework context leaked on completed worker");
                            pendingCompletions--;
                        }
                        return value;
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
            }, "admin", "admin", "10001", "order:pay", "order:cancel", "AWAITING_PAYMENT");
        }
    }

    /** \u9a8c\u8bc1<code>paid.path("orderState").asText()</code> \u7b49\u4e8e <code>"CONFIRMED"</code>\uff0c<code>driver.request("GET", ORDERS + id, null, Map.of()).path("paidAmountCent").asLong()</code> \u7b49\u4e8e <code>1000</code>\uff0c<code>anonymous.create("NOAUTH", Map.of())</code> \u629b\u51fa <code>SecurityException.class</code>\u3002 */
    @Test
    void originalOrderRoutesUseSharedOrderAndMutateBusiness() {
        try (var application = start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            var anonymous = new Driver(base, Map.of());
            String token = anonymous.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            var driver = new Driver(base, Map.of("ddd4j-token", token));
            String id = driver.create("AUTH-ORDER-" + UUID.randomUUID(), Map.of());
            var paid = driver.request("POST", ORDERS + id + "/payment", Map.of("paidAmountCent", 1000), Map.of());
            assertEquals("CONFIRMED", paid.path("orderState").asText());
            assertEquals(1000, driver.request("GET", ORDERS + id, null, Map.of()).path("paidAmountCent").asLong());
            assertThrows(SecurityException.class, () -> anonymous.create("NOAUTH", Map.of()));
            assertEquals("BOOK", driver.request("GET", GOODS + "BOOK", null, Map.of()).path("spuCode").asText());
        }
    }

    /**
     * \u9a8c\u8bc1<code>release.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>finished.poll(10, TimeUnit.SECONDS).isEmpty()</code>\u4e3a\u771f\uff0c<code>observed.isEmpty()</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void clientCancellationStillClosesNativeWorkerScope() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        LinkedBlockingQueue<Optional<String>> finished = new LinkedBlockingQueue<>();
        try (var app = start(0, finished::add, config -> config.routes.before(ctx -> {
            if (ctx.path().equals(ORDERS + "disconnect") && ctx.method() == io.javalin.http.HandlerType.GET) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
        }))) {
            String base = "http://127.0.0.1:" + app.app().port();
            var http = new Driver(base, Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            assertTrue(finished.poll(10, TimeUnit.SECONDS).isEmpty()); // startup native thread
            assertTrue(finished.poll(10, TimeUnit.SECONDS).isEmpty()); // login worker
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + ORDERS + "disconnect"))
                    .header("ddd4j-token", token).GET().build();
            var future = java.net.http.HttpClient.newHttpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                future.cancel(true);
            } finally {
                release.countDown();
            }
            Optional<String> observed = finished.poll(10, TimeUnit.SECONDS);
            assertNotNull(observed, "disconnect must finish the actual servlet scope");
            assertTrue(observed.isEmpty());
            assertEquals("10001", http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token)).path("loginId").asText());
            assertTrue(finished.poll(10, TimeUnit.SECONDS).isEmpty());
        } finally {
            release.countDown();
        }
    }

    /** \u9a8c\u8bc1<code>start(live.app().port())</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"))</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>nativeDao</code>\uff0c<code>io.ddd4j.core.util.SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
    @Test
    void failedStartRestoresLiveApplicationAndClosingRestoresPriorSpi() {
        var previous = io.ddd4j.core.util.SubjectKit.subjectProvider;
        try (var live = start(0)) {
            var owner = io.ddd4j.core.util.SubjectKit.subjectProvider;
            var injector = io.ddd4j.guice.context.GuiceContext.getInjector();
            Object nativeDao = ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
            String base = "http://127.0.0.1:" + live.app().port();
            var http = new Driver(base, Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            assertThrows(RuntimeException.class, () -> start(live.app().port()));
            assertSame(nativeDao, ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao")));
            assertEquals("10001", http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token)).path("loginId").asText());
            assertSame(owner, io.ddd4j.core.util.SubjectKit.subjectProvider);
            assertSame(injector, io.ddd4j.guice.context.GuiceContext.getInjector());
            assertFalse(http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText().isBlank());
        }
        assertSame(previous, io.ddd4j.core.util.SubjectKit.subjectProvider);
    }

    /** \u9a8c\u8bc1<code>io.ddd4j.core.context.ThreadContext.getResources()</code> \u7b49\u4e8e <code>expected</code>\uff0c<code>io.ddd4j.core.context.ThreadContext.getValues()</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void closePreservesCallingThreadsCompleteContext() {
        try (var original = io.ddd4j.core.context.ThreadContext.open()) {
            io.ddd4j.core.context.ThreadContext.put(new Object(), new Object());
            io.ddd4j.core.context.ThreadContext.set("caller-value", "preserved");
            var expected = io.ddd4j.core.context.ThreadContext.getResources();
            start(0).close();
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getResources());
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getValues());
        }
    }

    /** \u5c06 <code>json</code>\u3001<code>base</code>\u3001<code>credentials</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static final class Driver {
        /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
        private final ObjectMapper json = new ObjectMapper();
        /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
        private final String base;
        /** \u8ba4\u8bc1\u8bf7\u6c42\u6837\u672c\u643a\u5e26\u7684\u8d26\u53f7\u51ed\u636e\u3002 */
        private final Map<String, String> credentials;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Driver</code> \u5b9e\u4f8b\u3002
         * @param base \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740
         * @param credentials <code>credentials</code> \u5b57\u6bb5
         */
        Driver(String base, Map<String, String> credentials) {
            this.base = base;
            this.credentials = credentials;
        }

        /**
         * \u901a\u8fc7 <code>request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>request("POST", ORDERS + id, ORDER, headers).path("orderNo").asText()</code>
         */
        String create(String id, Map<String, String> headers) {
            return request("POST", ORDERS + id, ORDER, headers).path("orderNo").asText();
        }

        /**
         * \u901a\u8fc7 <code>result.has()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>result.has("data") ? result.path("data") : result</code>
         */
        JsonNode request(String method, String path, Object body, Map<String, String> headers) {
            var actualHeaders = new java.util.HashMap<>(credentials);
            actualHeaders.putAll(headers);
            try {
                var response = SampleHttpTransport.request(base + path, method, actualHeaders,
                        Map.of("X-Tenant-Id", TENANT), Objects.isNull(body) ? null : json.writeValueAsString(body));
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new SecurityException("HTTP " + response.statusCode());
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
                }
                var result = json.readTree(response.body());
                return result.has("data") ? result.path("data") : result;
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

}
