package io.ddd4j.sample.helidon.satoken;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.helidon.microprofile.server.Server;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务端只读观测：HTTP 认证授权、业务失败、原生线程边界及断连后结束。
 */
@Testcontainers
class NativeAuthHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    static final String TENANT = "helidon-auth";
    static final String ORDERS = "/orders/catalog/orders/";
    static final Map<String, Object> ORDER = Map.of("shopId", 7, "items", java.util.List.of(Map.of("skuCode", "S1", "quantity", 2)));
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final ConcurrentLinkedQueue<Boolean> CLEAN = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<Boolean> FINISHED = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<String> THREADS = new ConcurrentLinkedQueue<>();
    static final String HELD_REQUEST = "r14-disconnect-satoken";
    static final CompletableFuture<Boolean> HELD_FINISHED = new CompletableFuture<>();
    static final CountDownLatch ENTERED = new CountDownLatch(1), RELEASE = new CountDownLatch(1);

    static CatalogEndpoints catalog() {
        return CatalogRuntime.start(new UnpooledDataSource(MYSQL.getDriverClassName(),
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    static boolean present() {
        return cn.dev33.satoken.SaManager.getSaTokenContext().isValid();
    }

    static Object nativeOwner() {
        return cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(cn.dev33.satoken.SaManager.class, "saTokenDao"));
    }

    private static SampleHttpTransport.Response readOrder(String url, Map<String, String> headers)
            throws Exception {
        return SampleHttpTransport.request(url, "GET", headers, Map.of("X-Tenant-Id", TENANT), null);
    }

    private static void assertReader(SampleHttpTransport.Response response, String scheme) throws java.io.IOException {
        assertEquals(200, response.statusCode(), scheme);
        assertEquals("reader", JSON.readTree(response.body()).path("data").asText(), scheme);
    }

    private static void assertSameOrder(SampleHttpTransport.Response canonical, SampleHttpTransport.Response actual,
                                        String scheme) throws java.io.IOException {
        assertEquals(200, actual.statusCode(), scheme);
        assertEquals(JSON.readTree(canonical.body()), JSON.readTree(actual.body()), scheme);
    }

    @Test
    void actualNativeAuthenticationAndSharedOrder() throws Exception {
        var previousProvider = io.ddd4j.core.util.SubjectKit.subjectProvider;
        Object previousNative = nativeOwner();
        Server server = Server.builder().port(0).addApplication(new HelidonSaTokenApplication(catalog()) {
            @Override
            public Set<Class<?>> getClasses() {
                var classes = new HashSet<>(super.getClasses());
                classes.add(Observer.class);
                classes.add(Inner.class);
                return classes;
            }
        }).build().start();
        try {
            Driver driver = new Driver(server.port());
            assertEquals(401, driver.send("GET", "/auth/me", null, null).statusCode());
            assertEquals(401, driver.send("POST", "/auth/login", Map.of("username", "alice", "password", "wrong"), null).statusCode());
            String token = driver.login("alice", "sample-password");
            driver.seedGoods(token);
            driver.createOrder("native-order", token);
            assertEquals(200, driver.send("POST", ORDERS + "native-order/payment", Map.of("paidAmountCent", 1000), token).statusCode());
            assertEquals(200, driver.send("GET", ORDERS + "native-order", null, token).statusCode());
            driver.emptyOrder = "unpaid-order";
            driver.createOrder(driver.emptyOrder, token);
            SampleContractAssertions.authentication("satoken", driver, "alice", "sample-password", "alice", "order:read", "order:cancel", "orders");
            String reader = driver.login("reader", "sample-password");
            assertEquals(403, driver.send("POST", ORDERS + "forbidden", ORDER, reader).statusCode());
            token = driver.login("alice", "sample-password");
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                socket.getOutputStream().write(("GET /auth/me HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + token + "\r\nX-Test-Hold: " + HELD_REQUEST + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTrue(ENTERED.await(10, TimeUnit.SECONDS));
            } finally {
                RELEASE.countDown();
            }
            assertTrue(HELD_FINISHED.get(10, TimeUnit.SECONDS), "held request FINISHED must observe native and DDD cleanup");
            assertFalse(CLEAN.contains(false), THREADS.toString());
            System.out.println("S05 satoken server cleanup observations=" + CLEAN.size() + " enter/exit=" + THREADS);
        } finally {
            server.stop();
        }
        assertSame(previousProvider, io.ddd4j.core.util.SubjectKit.subjectProvider);
        assertSame(previousNative, nativeOwner());
        assertFalse(FINISHED.isEmpty());
        assertFalse(FINISHED.contains(false));
        System.out.println("S05 FINISHED observations=" + FINISHED.size());
    }

    @Test
    void unsupportedOrderMethodPreservesNativeAllowHeader() throws Exception {
        Server server = Server.builder().port(0).addApplication(new HelidonSaTokenApplication(catalog())).build().start();
        try {
            String order = ORDERS + "parity-probe";
            Driver driver = new Driver(server.port());
            assertEquals(404, driver.send("GET", order + "/no-route", null, null).statusCode());
            assertEquals(401, driver.send("GET", order, null, null).statusCode());

            HttpURLConnection connection = (HttpURLConnection) new URL(driver.base + order + "/payment").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try {
                assertEquals(405, connection.getResponseCode());
                try (java.io.InputStream body = connection.getErrorStream()) {
                    assertEquals(405, JSON.readTree(body).path("code").asInt());
                }
                String allow = connection.getHeaderField("Allow");
                assertNotNull(allow, "Jersey's Allow header must survive JSON error translation");
                Set<String> allowedMethods = Arrays.stream(allow.split(",")).map(String::trim).collect(Collectors.toSet());
                assertEquals(new HashSet<>(Arrays.asList("POST", "OPTIONS")), allowedMethods);
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        Server server = Server.builder().port(0).addApplication(new HelidonSaTokenApplication(catalog())).build().start();
        try {
            Driver driver = new Driver(server.port());
            String writerToken = driver.login("alice", "sample-password");
            assertEquals("alice", driver.authenticatedPrincipal(writerToken));
            driver.seedGoods(writerToken);
            String id = "bearer-scheme";
            driver.createOrder(id, writerToken);
            String token = driver.login("reader", "sample-password");
            assertEquals("reader", driver.authenticatedPrincipal(token));
            String url = driver.base + ORDERS + id;
            SampleHttpTransport.Response canonical = readOrder(url, Map.of("Authorization", "Bearer " + token));
            assertEquals(200, canonical.statusCode());
            assertEquals(id, JSON.readTree(canonical.body()).path("orderNo").asText());
            String tokenName = StpUtil.getTokenName();
            String principalUrl = driver.base + "/auth/me";
            assertAll("authorization schemes for the same order and token",
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bearer " + token)), "bearer"),
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bEaReR " + token)), "bEaReR"),
                    () -> assertReader(readOrder(principalUrl, Map.of("Authorization", "bearer " + token)), "bearer /auth/me"),
                    () -> assertReader(readOrder(principalUrl, Map.of("Authorization", "bEaReR " + token)), "bEaReR /auth/me"),
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces"),
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces"),
                    () -> assertReader(readOrder(principalUrl, Map.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces /auth/me"),
                    () -> assertReader(readOrder(principalUrl, Map.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces /auth/me"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer")).statusCode(),
                            "Bearer without token"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer    ")).statusCode(),
                            "Bearer with only separator spaces"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer   invalid-token")).statusCode(),
                            "invalid Bearer token"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer   invalid-token",
                            tokenName, token)).statusCode(), "invalid Bearer with native token header"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
                    () -> assertEquals(401, readOrder(url, Map.of()).statusCode(), "without credentials"),
                    () -> assertEquals(401, readOrder(url, Map.of(tokenName, token)).statusCode(), "native token header alone"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "BearerX " + token,
                            tokenName, token)).statusCode(), "unsupported scheme with native token header"));
        } finally {
            server.stop();
        }
    }

    static class Driver implements AuthSampleDriver {
        final String base;
        final HttpClient client = HttpClient.newHttpClient();
        String emptyOrder;

        Driver(int port) {
            base = "http://127.0.0.1:" + port;
        }

        HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", TENANT);
            if (Objects.nonNull(token)) {
                builder.header("Authorization", "Bearer " + token);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        String request(String method, String path, Object body, String token) {
            try {
                var response = send(method, path, body, token);
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new SecurityException(response.body());
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException(response.statusCode() + " " + response.body());
                }
                return JSON.readTree(response.body()).path("data").asText();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public String login(String user, String password) {
            try {
                return JSON.readTree(send("POST", "/auth/login", Map.of("username", user, "password", password), null).body()).path("data").path("token").asText();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public String authenticatedPrincipal(String token) {
            return request("GET", "/auth/me", null, token);
        }

        @Override
        public String authorizedResource(String token, String permission) {
            return request("GET", "/auth/resource?permission=" + permission, null, token);
        }

        void seedGoods(String token) throws Exception {
            var response = send("POST", "/orders/catalog/goods/BOOK", Map.of(
                    "spuName", "Book", "skuCode", "S1", "skuName", "Paperback", "shopId", 7, "salePriceCent", 500), token);
            assertTrue(response.statusCode() == 201 || response.statusCode() == 409, response.body());
        }

        void createOrder(String id, String token) throws Exception {
            var response = send("POST", ORDERS + id, ORDER, token);
            assertEquals(201, response.statusCode(), response.body());
        }

        @Override
        public void failingRequest(String token) {
            request("POST", ORDERS + emptyOrder + "/payment", Map.of("paidAmountCent", 1), token);
        }

        @Override
        public void logout(String token) {
            request("POST", "/auth/logout", null, token);
        }

        @Override
        public Optional<String> contextPrincipal() {
            return CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty();
        }
    }

    @Provider
    @Priority(800)
    public static class Observer implements ContainerRequestFilter, ContainerResponseFilter, org.glassfish.jersey.server.monitoring.ApplicationEventListener {
        @Override
        public void onEvent(org.glassfish.jersey.server.monitoring.ApplicationEvent event) {
        }

        @Override
        public org.glassfish.jersey.server.monitoring.RequestEventListener onRequest(org.glassfish.jersey.server.monitoring.RequestEvent event) {
            return finished -> {
                if (finished.getType() == org.glassfish.jersey.server.monitoring.RequestEvent.Type.FINISHED) {
                    boolean clean = !present() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject());
                    FINISHED.add(clean);
                    if (HELD_REQUEST.equals(finished.getContainerRequest().getHeaderString("X-Test-Hold"))) {
                        HELD_FINISHED.complete(clean);
                    }
                }
            };
        }

        @Override
        public void filter(ContainerRequestContext request) {
            request.setProperty("entry-thread", Thread.currentThread().getName());
            CLEAN.add(!present());
        }

        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            CLEAN.add(!present() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
            THREADS.add(request.getProperty("entry-thread") + " -> " + Thread.currentThread().getName());
        }
    }

    @Provider
    @Priority(1100)
    public static class Inner implements ContainerRequestFilter {
        @Override
        public void filter(ContainerRequestContext request) {
            assertTrue(present());
            if (!request.getUriInfo().getPath().equals("auth/login")) {
                assertEquals(cn.dev33.satoken.stp.StpUtil.hasPermission("order:read"), io.ddd4j.core.util.SubjectKit.getSubject().isPermitted("order:read"));
            }
            if (Objects.nonNull(request.getHeaderString("X-Test-Hold"))) {
                ENTERED.countDown();
                try {
                    assertTrue(RELEASE.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
        }
    }
}
