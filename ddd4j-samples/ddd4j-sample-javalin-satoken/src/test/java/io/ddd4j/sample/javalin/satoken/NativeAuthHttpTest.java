package io.ddd4j.sample.javalin.satoken;

import cn.dev33.satoken.SaManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.fun.strategy.SaCreateSaRequestFunction;
import cn.dev33.satoken.fun.strategy.SaCreateSaResponseFunction;
import cn.dev33.satoken.fun.strategy.SaCreateSaStorageFunction;
import cn.dev33.satoken.strategy.SaStrategy;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.kit.lang.StrKit;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Objects;
import java.time.Duration;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** 实际 main/start 装配、原生认证和共享订单 HTTP 验收。 */
class NativeAuthHttpTest {
    @Test
    void orderAuthorizationDenialsUseTheOrderEnvelope() throws Exception {
        try (JavalinSaTokenApplication.Application application = JavalinSaTokenApplication.start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            EnvelopeOrderHttpDriver http = new EnvelopeOrderHttpDriver(base, "/orders", Map.of());
            var credentials = Map.of("username", "admin", "password", "admin");
            String writer = http.request("POST", "/auth/login", credentials, Map.of()).path("token").asText();
            assertTrue(StrKit.hasText(writer), "native authentication returns a real token");
            Map<String, String> headers = Map.of("ddd4j-token", writer);
            CreateOrderCommand order = new CreateOrderCommand("DENIAL-" + UUID.randomUUID(), "buyer-1", "Alice");
            String id = http.request("POST", "/orders", order, headers).path("id").asText();
            ObjectMapper mapper = new ObjectMapper();
            String anonymousBody = mapper.writeValueAsString(
                    new CreateOrderCommand("ANON-" + UUID.randomUUID(), "buyer-1", "Alice"));

            assertAll(
                    () -> assertOrderDenial(base, "/orders", Map.of(), anonymousBody,
                            401, "not login", mapper),
                    () -> assertOrderDenial(base, "/orders/" + id + "/cancel", headers, null,
                            403, "required permission is missing", mapper),
                    () -> assertEquals("DRAFT", http.request("GET", "/orders/" + id, null, headers).path("status").asText()));
        }
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
                .put("success", false).put("empty", true).put("ok", false);
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

    @Test
    void missingSharedOrderReturnsTheOrderDiagnostic() throws IOException {
        try (JavalinSaTokenApplication.Application application = JavalinSaTokenApplication.start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            EnvelopeOrderHttpDriver http = new EnvelopeOrderHttpDriver(base, "/orders", Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"),
                    Map.of()).path("token").asText();
            assertFalse(token.isEmpty(), "native authentication returns a real token");
            Map<String, String> headers = Map.of("ddd4j-token", token);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode expected = mapper.readTree("{\"code\":500,\"msg\":\"order not found\",\"data\":null,\"success\":false,\"ok\":false,\"empty\":true}");

            assertAll(
                    () -> assertMissingOrder(base, "/orders/missing-order", headers, mapper, expected),
                    () -> assertMissingOrder(base, "/orders/by-order-no?orderNo=MISSING-ORDER", headers, mapper, expected),
                    () -> assertMissingOrder(base, "/orders/missing-order/discount", headers, mapper, expected));
        }
    }

    private static void assertMissingOrder(String base, String path, Map<String, String> headers,
                                           ObjectMapper mapper, JsonNode expected) throws IOException {
        SampleHttpTransport.Response response = SampleHttpTransport.request(
                base + path, "GET", headers, Map.of(), null);
        assertEquals(500, response.statusCode(), path + " " + response.body());
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

    @Test
    void nativeAuthenticationPreservesCallerServletFactories() {
        SaStrategy strategy = SaStrategy.instance;
        var previousRequest = strategy.createSaRequest;
        var previousResponse = strategy.createSaResponse;
        var previousStorage = strategy.createSaStorage;
        SaCreateSaRequestFunction callerRequest = source -> fail("caller owns the request factory");
        SaCreateSaResponseFunction callerResponse = source -> fail("caller owns the response factory");
        SaCreateSaStorageFunction callerStorage = source -> fail("caller owns the storage factory");
        strategy.setCreateSaRequest(callerRequest).setCreateSaResponse(callerResponse).setCreateSaStorage(callerStorage);
        try {
            try (var application = JavalinSaTokenApplication.start(0)) {
                assertSame(callerRequest, strategy.createSaRequest);
                assertSame(callerResponse, strategy.createSaResponse);
                assertSame(callerStorage, strategy.createSaStorage);
                var http = new EnvelopeOrderHttpDriver("http://127.0.0.1:" + application.app().port(), "/orders", Map.of());
                String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of())
                        .path("token").asText();
                assertEquals("10001", http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token))
                        .path("loginId").asText());
            }
            assertSame(callerRequest, strategy.createSaRequest);
            assertSame(callerResponse, strategy.createSaResponse);
            assertSame(callerStorage, strategy.createSaStorage);
        } finally {
            strategy.setCreateSaRequest(previousRequest).setCreateSaResponse(previousResponse).setCreateSaStorage(previousStorage);
        }
    }

    @Test
    void cancellationAuthorizationUsesMatchedRouteForBothSlashForms() throws Exception {
        LinkedBlockingQueue<Optional<String>> completed = new LinkedBlockingQueue<>();
        try (var application = JavalinSaTokenApplication.start(0, completed::add)) {
            var http = new EnvelopeOrderHttpDriver("http://127.0.0.1:" + application.app().port(), "/orders", Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            var headers = Map.of("ddd4j-token", token);
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS)); // startup
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS)); // login
            List<String> suffixes = List.of("/cancel", "/cancel/");
            List<String> ids = new ArrayList<>();
            for (String suffix : suffixes) {
                String id = http.request("POST", "/orders", new CreateOrderCommand("CANCEL-" + UUID.randomUUID(), "buyer-1", "Alice"), headers).path("id").asText();
                ids.add(id);
                SecurityException denied = assertThrows(SecurityException.class,
                        () -> http.request("POST", "/orders/" + id + suffix, null, headers));
                assertEquals("HTTP 403", denied.getMessage());
                assertEquals("DRAFT", http.request("GET", "/orders/" + id, null, headers).path("status").asText());
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
                assertEquals("CANCELLED", http.request("POST", "/orders/" + id + suffixes.get(index), null, allowedHeaders).path("status").asText());
                assertEquals("CANCELLED", http.request("GET", "/orders/" + id, null, allowedHeaders).path("status").asText());
                for (int request = 0; request < 2; request++) {
                    assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    void nativeAuthenticationAndBusinessCleanup() throws Exception {
        LinkedBlockingQueue<Optional<String>> completed = new LinkedBlockingQueue<>();
        try (var application = JavalinSaTokenApplication.start(0, completed::add)) {
            var http = new EnvelopeOrderHttpDriver("http://127.0.0.1:" + application.app().port(), "/orders", Map.of());
            SampleContractAssertions.authentication("satoken", new AuthSampleDriver() {
                private String orderId;
                private int pendingCompletions = 1; // startup observation
                @Override public String login(String username, String password) {
                    pendingCompletions++;
                    return http.request("POST", "/auth/login", Map.of("username", username, "password", password), Map.of()).path("token").asText();
                }
                @Override public String authenticatedPrincipal(String token) {
                    pendingCompletions++;
                    return http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token)).path("loginId").asText();
                }
                @Override public String authorizedResource(String token, String permission) {
                    if ("order:cancel".equals(permission)) {
                        pendingCompletions++;
                        http.request("POST", "/orders/" + orderId + "/cancel", null, Map.of("ddd4j-token", token));
                        return "unexpected";
                    }
                    pendingCompletions += 2;
                    orderId = http.request("POST", "/orders", new CreateOrderCommand("AUTH-" + UUID.randomUUID(), "buyer-1", "Alice"),
                            Map.of("ddd4j-token", token)).path("id").asText();
                    return http.request("GET", "/orders/" + orderId, null, Map.of("ddd4j-token", token)).path("buyerName").asText();
                }
                @Override public void failingRequest(String token) {
                    pendingCompletions++;
                    // 已认证的真实业务失败：空订单支付被共享领域拒绝。
                    http.request("POST", "/orders/" + orderId + "/pay", null, Map.of("ddd4j-token", token));
                }
                @Override public void logout(String token) {
                    pendingCompletions++;
                    http.request("POST", "/auth/logout", null, Map.of("ddd4j-token", token));
                }
                @Override public Optional<String> contextPrincipal() {
                    try {
                        Optional<String> value = null;
                        while (pendingCompletions > 0) {
                            value = completed.poll(10, TimeUnit.SECONDS);
                            assertNotNull(value, "every native request must produce its completion observation");
                            assertTrue(value.isEmpty(), "native or framework context leaked on completed worker");
                            pendingCompletions--;
                        }
                        return value;
                    } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                }
            }, "admin", "admin", "10001", "order:pay", "order:cancel", "Alice");
        }
    }

    @Test
    void originalOrderRoutesUseSharedOrderAndMutateBusiness() {
        try (var application = JavalinSaTokenApplication.start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            var anonymous = new EnvelopeOrderHttpDriver(base, "/orders", Map.of());
            String token = anonymous.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            var driver = new EnvelopeOrderHttpDriver(base, "/orders", Map.of("ddd4j-token", token));
            SampleContractAssertions.defaultOrder(driver, "AUTH-ORDER-" + UUID.randomUUID());
            assertThrows(SecurityException.class, () -> anonymous.create(new CreateOrderCommand("NOAUTH", "b", "n")));
            driver.request("POST", "/api/goods", Map.of("code", "native-goods", "name", "Native goods", "price", 10, "stock", 4), Map.of());
            assertTrue(driver.request("GET", "/api/goods/list", null, Map.of()).toString().contains("native-goods"),
                    "main Goods write/query must share the same repository");
        }
    }
    @Test
    void clientCancellationStillClosesNativeWorkerScope() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        LinkedBlockingQueue<Optional<String>> finished = new LinkedBlockingQueue<>();
        try (var app = JavalinSaTokenApplication.start(0, finished::add, config -> config.routes.before(ctx -> {
            if (ctx.path().equals("/orders") && ctx.method() == io.javalin.http.HandlerType.GET) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
        }))) {
            String base = "http://127.0.0.1:" + app.app().port();
            var http = new EnvelopeOrderHttpDriver(base, "/orders", Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            assertTrue(finished.poll(10, TimeUnit.SECONDS).isEmpty()); // startup native thread
            assertTrue(finished.poll(10, TimeUnit.SECONDS).isEmpty()); // login worker
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/orders"))
                    .header("ddd4j-token", token).GET().build();
            var future = java.net.http.HttpClient.newHttpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                future.cancel(true);
            } finally { release.countDown(); }
            Optional<String> observed = finished.poll(10, TimeUnit.SECONDS);
            assertNotNull(observed, "disconnect must finish the actual servlet scope");
            assertTrue(observed.isEmpty());
            assertEquals("10001", http.request("GET", "/auth/me", null, Map.of("ddd4j-token", token)).path("loginId").asText());
            assertTrue(finished.poll(10, TimeUnit.SECONDS).isEmpty());
        } finally { release.countDown(); }
    }

    @Test
    void failedStartRestoresLiveApplicationAndClosingRestoresPriorSpi() {
        var previous = io.ddd4j.core.util.SubjectKit.subjectProvider;
        try (var live = JavalinSaTokenApplication.start(0)) {
            var owner = io.ddd4j.core.util.SubjectKit.subjectProvider;
            var injector = io.ddd4j.guice.context.GuiceContext.getInjector();
            Object nativeDao = ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
            String base = "http://127.0.0.1:" + live.app().port();
            var http = new EnvelopeOrderHttpDriver(base, "/orders", Map.of());
            String token = http.request("POST", "/auth/login", Map.of("username", "admin", "password", "admin"), Map.of()).path("token").asText();
            assertThrows(RuntimeException.class, () -> JavalinSaTokenApplication.start(live.app().port()));
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
            JavalinSaTokenApplication.start(0).close();
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getResources());
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getValues());
        }
    }

}
