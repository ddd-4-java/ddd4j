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
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    private static final String ORDERS = "/orders/catalog/orders/";
    private static final String GOODS = "/orders/catalog/goods/";
    private static final String TENANT = "javalin-auth";
    private static final Map<String, Object> ORDER = Map.of("shopId", 7,
            "items", List.of(Map.of("skuCode", "SKU-1", "quantity", 2)));

    static CatalogEndpoints catalog(MySQLContainer<?> database) {
        var api = CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(),
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        int status = api.createGoods(TENANT, "BOOK", """
                {"spuName":"Book","skuCode":"SKU-1","skuName":"Edition","shopId":7,"salePriceCent":500}
                """).getStatus();
        assertTrue(status == 201 || status == 409);
        return api;
    }

    private static JavalinSaTokenApplication.Application start(int port) {
        return start(port, ignored -> {
        });
    }

    private static JavalinSaTokenApplication.Application start(int port,
                                                               java.util.function.Consumer<Optional<String>> observer) {
        return start(port, observer, config -> {
        });
    }

    private static JavalinSaTokenApplication.Application start(int port,
                                                               java.util.function.Consumer<Optional<String>> observer,
                                                               java.util.function.Consumer<io.javalin.config.JavalinConfig> configure) {
        return JavalinSaTokenApplication.start(port, observer, configure, catalog(MYSQL));
    }

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
                .put("empty", true).put("ok", false);
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

    private static void assertMissingOrder(String base, String path, Map<String, String> headers,
                                           ObjectMapper mapper, JsonNode expected) throws IOException {
        SampleHttpTransport.Response response = SampleHttpTransport.request(
                base + path, "GET", headers, Map.of("X-Tenant-Id", TENANT), null);
        assertEquals(404, response.statusCode(), path + " " + response.body());
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

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

    static final class Driver {
        private final ObjectMapper json = new ObjectMapper();
        private final String base;
        private final Map<String, String> credentials;

        Driver(String base, Map<String, String> credentials) {
            this.base = base;
            this.credentials = credentials;
        }

        String create(String id, Map<String, String> headers) {
            return request("POST", ORDERS + id, ORDER, headers).path("orderNo").asText();
        }

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
