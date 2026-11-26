package io.ddd4j.sample.javalin.shiro;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Map<String, Object> ORDER = ImmutableMap.of("shopId", 7,
            "items", ImmutableList.of(ImmutableMap.of("skuCode", "SKU-1", "quantity", 2)));

    static CatalogEndpoints catalog(MySQLContainer<?> database) {
        io.ddd4j.sample.order.adapter.http.CatalogApi api = CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(),
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        int status = api.createGoods(TENANT, "BOOK", "{\"spuName\":\"Book\",\"skuCode\":\"SKU-1\",\"skuName\":\"Edition\",\"shopId\":7,\"salePriceCent\":500}\n").getStatus();
        assertTrue(status == 201 || status == 409);
        return api;
    }

    private static JavalinShiroApplication.Application start(int port) {
        return start(port, ignored -> {
        });
    }

    private static JavalinShiroApplication.Application start(int port,
                                                             java.util.function.Consumer<Optional<String>> observer) {
        return start(port, observer, config -> {
        });
    }

    private static JavalinShiroApplication.Application start(int port,
                                                             java.util.function.Consumer<Optional<String>> observer,
                                                             java.util.function.Consumer<io.javalin.Javalin> configure) {
        return JavalinShiroApplication.start(port, observer, configure, catalog(MYSQL));
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

    private static void assertMissingOrder(String base, String path, Map<String, String> headers,
                                           ObjectMapper mapper, JsonNode expected) throws IOException {
        SampleHttpTransport.Response response = SampleHttpTransport.request(
                base + path, "GET", headers, ImmutableMap.of("X-Tenant-Id", TENANT), null);
        assertEquals(404, response.statusCode(), path + " " + response.body());
        assertEquals(expected, mapper.readTree(response.body()), path);
    }

    @Test
    void orderAuthorizationDenialsUseTheOrderEnvelope() throws Exception {
        try (JavalinShiroApplication.Application application = start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            Driver http = new Driver(base, ImmutableMap.of());
            java.util.Map<java.lang.String, java.lang.String> credentials = ImmutableMap.of("loginId", "zhangsan", "password", "pass123");
            String writer = http.request("POST", "/auth/login", credentials, ImmutableMap.of()).path("token").asText();
            assertTrue(StrKit.hasText(writer), "native authentication returns a real token");
            Map<String, String> headers = ImmutableMap.of("ddd4j-token", writer);
            String id = http.create("DENIAL-" + UUID.randomUUID(), headers);
            ObjectMapper mapper = new ObjectMapper();
            String anonymousBody = mapper.writeValueAsString(ORDER);

            assertAll(
                    () -> assertOrderDenial(base, ORDERS + "ANONYMOUS", ImmutableMap.of(), anonymousBody,
                            401, "unauthenticated", mapper),
                    () -> assertOrderDenial(base, ORDERS + id + "/cancel", headers, null,
                            403, "required permission is missing", mapper),
                    () -> assertEquals("AWAITING_PAYMENT", http.request("GET", ORDERS + id, null, headers).path("orderState").asText()));
        }
    }

    @Test
    void missingSharedOrderReturnsTheOrderDiagnostic() throws IOException {
        try (JavalinShiroApplication.Application application = start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            Driver http = new Driver(base, ImmutableMap.of());
            String token = http.request("POST", "/auth/login", ImmutableMap.of("loginId", "admin", "password", "admin123"),
                    ImmutableMap.of()).path("token").asText();
            assertFalse(token.isEmpty(), "native authentication returns a real token");
            Map<String, String> headers = ImmutableMap.of("ddd4j-token", token);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode expected = mapper.createObjectNode().put("error", "记录不存在或规格在此门店不可售");
            assertMissingOrder(base, ORDERS + "missing-order", headers, mapper, expected);
        }
    }

    @Test
    void cancellationAuthorizationUsesMatchedRouteForBothSlashForms() throws Exception {
        LinkedBlockingQueue<Optional<String>> completed = new LinkedBlockingQueue<>();
        try (io.ddd4j.sample.javalin.shiro.JavalinShiroApplication.Application application = start(0, completed::add)) {
            Driver http = new Driver("http://127.0.0.1:" + application.app().port(), ImmutableMap.of());
            String token = http.request("POST", "/auth/login", ImmutableMap.of("loginId", "zhangsan", "password", "pass123"), ImmutableMap.of()).path("token").asText();
            java.util.Map<java.lang.String, java.lang.String> headers = ImmutableMap.of("ddd4j-token", token);
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS)); // startup
            assertEquals(Optional.empty(), completed.poll(10, TimeUnit.SECONDS)); // login
            List<String> suffixes = ImmutableList.of("/cancel", "/cancel/");
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
            String authorized = http.request("POST", "/auth/login", ImmutableMap.of("loginId", "admin", "password", "admin123"), ImmutableMap.of()).path("token").asText();
            java.util.Map<java.lang.String, java.lang.String> allowedHeaders = ImmutableMap.of("ddd4j-token", authorized);
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
        try (io.ddd4j.sample.javalin.shiro.JavalinShiroApplication.Application application = start(0, completed::add)) {
            Driver http = new Driver("http://127.0.0.1:" + application.app().port(), ImmutableMap.of());
            SampleContractAssertions.authentication("shiro", new AuthSampleDriver() {
                private String orderId;
                private int pendingCompletions = 1; // startup observation

                @Override
                public String login(String username, String password) {
                    pendingCompletions++;
                    return http.request("POST", "/auth/login", ImmutableMap.of("loginId", username, "password", password), ImmutableMap.of()).path("token").asText();
                }

                @Override
                public String authenticatedPrincipal(String token) {
                    pendingCompletions++;
                    return http.request("GET", "/auth/me", null, ImmutableMap.of("ddd4j-token", token)).path("loginId").asText();
                }

                @Override
                public String authorizedResource(String token, String permission) {
                    if ("order:cancel".equals(permission)) {
                        pendingCompletions++;
                        http.request("POST", ORDERS + orderId + "/cancel", null, ImmutableMap.of("ddd4j-token", token));
                        return "unexpected";
                    }
                    pendingCompletions += 2;
                    orderId = http.create("AUTH-" + UUID.randomUUID(), ImmutableMap.of("ddd4j-token", token));
                    return http.request("GET", ORDERS + orderId, null, ImmutableMap.of("ddd4j-token", token)).path("orderState").asText();
                }

                @Override
                public void failingRequest(String token) {
                    pendingCompletions++;
                    // 已认证的真实业务失败：金额不匹配被交易领域拒绝。
                    http.request("POST", ORDERS + orderId + "/payment", ImmutableMap.of("paidAmountCent", 1), ImmutableMap.of("ddd4j-token", token));
                }

                @Override
                public void logout(String token) {
                    pendingCompletions++;
                    http.request("POST", "/auth/logout", null, ImmutableMap.of("ddd4j-token", token));
                }

                @Override
                public Optional<String> contextPrincipal() {
                    try {
                        Optional<String> value = null;
                        while (pendingCompletions > 0) {
                            value = completed.poll(10, TimeUnit.SECONDS);
                            assertNotNull(value, "every native request must produce its completion observation");
                            assertTrue(!value.isPresent(), "native or framework context leaked on completed worker");
                            pendingCompletions--;
                        }
                        return value;
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
            }, "zhangsan", "pass123", "zhangsan", "order:pay", "order:cancel", "AWAITING_PAYMENT");
        }
    }

    @Test
    void originalOrderRoutesUseSharedOrderAndMutateBusiness() {
        try (io.ddd4j.sample.javalin.shiro.JavalinShiroApplication.Application application = start(0)) {
            String base = "http://127.0.0.1:" + application.app().port();
            Driver anonymous = new Driver(base, ImmutableMap.of());
            String token = anonymous.request("POST", "/auth/login", ImmutableMap.of("loginId", "zhangsan", "password", "pass123"), ImmutableMap.of()).path("token").asText();
            Driver driver = new Driver(base, ImmutableMap.of("ddd4j-token", token));
            String id = driver.create("AUTH-ORDER-" + UUID.randomUUID(), ImmutableMap.of());
            JsonNode paid = driver.request("POST", ORDERS + id + "/payment", ImmutableMap.of("paidAmountCent", 1000), ImmutableMap.of());
            assertEquals("CONFIRMED", paid.path("orderState").asText());
            assertEquals(1000, driver.request("GET", ORDERS + id, null, ImmutableMap.of()).path("paidAmountCent").asLong());
            assertThrows(SecurityException.class, () -> anonymous.create("NOAUTH", ImmutableMap.of()));
            assertEquals("BOOK", driver.request("GET", GOODS + "BOOK", null, ImmutableMap.of()).path("spuCode").asText());
        }
    }

    @Test
    void clientCancellationStillClosesNativeWorkerScope() throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        LinkedBlockingQueue<Optional<String>> finished = new LinkedBlockingQueue<>();
        try (io.ddd4j.sample.javalin.shiro.JavalinShiroApplication.Application app = start(0, finished::add, config -> config.before(ctx -> {
            if (ctx.path().equals(ORDERS + "disconnect") && "GET".equals(ctx.method())) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
        }))) {
            String base = "http://127.0.0.1:" + app.app().port();
            Driver http = new Driver(base, ImmutableMap.of());
            String token = http.request("POST", "/auth/login", ImmutableMap.of("loginId", "zhangsan", "password", "pass123"), ImmutableMap.of()).path("token").asText();
            assertTrue(!finished.poll(10, TimeUnit.SECONDS).isPresent()); // startup native thread
            assertTrue(!finished.poll(10, TimeUnit.SECONDS).isPresent()); // login worker
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + ORDERS + "disconnect"))
                    .header("ddd4j-token", token).GET().build();
            java.util.concurrent.CompletableFuture<HttpResponse<java.lang.String>> future = HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                future.cancel(true);
            } finally {
                release.countDown();
            }
            Optional<String> observed = finished.poll(10, TimeUnit.SECONDS);
            assertNotNull(observed, "disconnect must finish the actual servlet scope");
            assertTrue(!observed.isPresent());
            assertEquals("zhangsan", http.request("GET", "/auth/me", null, ImmutableMap.of("ddd4j-token", token)).path("loginId").asText());
            assertTrue(!finished.poll(10, TimeUnit.SECONDS).isPresent());
        } finally {
            release.countDown();
        }
    }

    @Test
    void failedStartRestoresLiveApplicationAndClosingRestoresPriorSpi() {
        io.ddd4j.core.subject.SubjectProvider previous = SubjectKit.subjectProvider;
        try (io.ddd4j.sample.javalin.shiro.JavalinShiroApplication.Application live = start(0)) {
            io.ddd4j.core.subject.SubjectProvider owner = SubjectKit.subjectProvider;
            Injector injector = GuiceContext.getInjector();
            assertThrows(RuntimeException.class, () -> start(live.app().port()));
            assertSame(owner, SubjectKit.subjectProvider);
            assertSame(injector, GuiceContext.getInjector());
            String base = "http://127.0.0.1:" + live.app().port();
            Driver http = new Driver(base, ImmutableMap.of());
            assertFalse(StrKit.isBlank(http.request("POST", "/auth/login", ImmutableMap.of("loginId", "zhangsan", "password", "pass123"), ImmutableMap.of()).path("token").asText()));
        }
        assertSame(previous, SubjectKit.subjectProvider);
    }

    @Test
    void startupFailureKeepsItsCauseWhenServerCleanupAlsoFails() throws Exception {
        Injector previousInjector = GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
        Injector caller = Guice.createInjector();
        io.ddd4j.core.subject.SubjectProvider previousProvider = SubjectKit.subjectProvider;
        io.ddd4j.core.subject.SubjectDataProvider previousDataProvider = SubjectKit.dataProvider;
        io.ddd4j.core.subject.SubjectStrategy previousStrategy = SubjectKit.strategy;
        AtomicInteger startedPort = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AssertionError startup = new AssertionError("serverStarted failure");
        RuntimeException cleanup = new IllegalStateException("serverStopped failure");

        GuiceContext.setInjector(caller);
        try {
            AtomicReference<Server> server = new AtomicReference<>();
            AssertionError thrown = assertThrows(AssertionError.class, () ->
                    start(0, ignored -> {
                    }, config -> {
                        config.jettyServer().setServerHost("127.0.0.1");
                        server.set(config.jettyServer().server());
                        config.events(events -> events.serverStarted(() -> {
                            ServerConnector connector = (ServerConnector) server.get().getConnectors()[0];
                            startedPort.set(connector.getLocalPort());
                            assertNotSame(caller, GuiceContext.getInjector());
                            assertNotSame(previousProvider, SubjectKit.subjectProvider);
                            throw startup;
                        }));
                        config.events(events -> events.serverStopped(() -> {
                            stops.incrementAndGet();
                            throw cleanup;
                        }));
                    }));
            assertAll(
                    () -> assertSame(startup, thrown),
                    () -> assertArrayEquals(new Throwable[]{cleanup}, thrown.getSuppressed()),
                    () -> assertTrue(startedPort.get() > 0, "Jetty must have bound a real socket before the failure"),
                    () -> assertEquals(1, stops.get()),
                    () -> assertSame(caller, GuiceContext.isInitialized() ? GuiceContext.getInjector() : null),
                    () -> assertSame(previousProvider, SubjectKit.subjectProvider),
                    () -> assertSame(previousDataProvider, SubjectKit.dataProvider),
                    () -> assertSame(previousStrategy, SubjectKit.strategy)
            );
            try (Socket socket = new Socket()) {
                assertThrows(ConnectException.class, () ->
                        socket.connect(new InetSocketAddress("127.0.0.1", startedPort.get()), 1_000));
            }
        } finally {
            SubjectKit.setStrategy(previousStrategy);
            SubjectKit.setDataProvider(previousDataProvider);
            SubjectKit.subjectProvider = previousProvider;
            if (Objects.isNull(previousInjector)) {
                GuiceContext.clear();
            } else {
                GuiceContext.setInjector(previousInjector);
            }
        }
    }

    @Test
    void closePreservesCallingThreadsCompleteContext() {
        try (io.ddd4j.core.context.ThreadContext.Scope original = io.ddd4j.core.context.ThreadContext.open()) {
            io.ddd4j.core.context.ThreadContext.put(new Object(), new Object());
            io.ddd4j.core.context.ThreadContext.set("caller-value", "preserved");
            java.util.Map<java.lang.Object, java.lang.Object> expected = io.ddd4j.core.context.ThreadContext.getResources();
            start(0).close();
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getResources());
            assertEquals(expected, io.ddd4j.core.context.ThreadContext.getValues());
        }
    }

    private static final class Driver {
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
            java.util.HashMap<java.lang.String, java.lang.String> actualHeaders = new java.util.HashMap<>(credentials);
            actualHeaders.putAll(headers);
            try {
                SampleHttpTransport.Response response = SampleHttpTransport.request(base + path, method, actualHeaders,
                        ImmutableMap.of("X-Tenant-Id", TENANT), Objects.isNull(body) ? null : json.writeValueAsString(body));
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new SecurityException("HTTP " + response.statusCode());
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
                }
                JsonNode result = json.readTree(response.body());
                return result.has("data") ? result.path("data") : result;
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

}
