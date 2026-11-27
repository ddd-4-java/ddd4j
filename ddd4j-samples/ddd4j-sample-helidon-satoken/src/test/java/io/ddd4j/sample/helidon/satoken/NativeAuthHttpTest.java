package io.ddd4j.sample.helidon.satoken;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.helidon.microprofile.server.Server;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
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
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u7528\u4f8b\u67e5\u8be2\u6216\u5199\u5165\u6570\u636e\u65f6\u4f7f\u7528\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    static final String TENANT = "helidon-auth";
    /** \u7528\u4e8e\u533a\u5206\u8ba2\u5355\u8def\u7531\u3001\u6d88\u606f\u4e3b\u9898\u6216\u6295\u5f71\u4e8b\u4ef6\u6d41\u7684\u6d4b\u8bd5\u540d\u79f0\u3002 */
    static final String ORDERS = "/orders/catalog/orders/";
    /** \u5f53\u524d\u5408\u540c\u6216\u4ed3\u50a8\u64cd\u4f5c\u6240\u4f7f\u7528\u7684\u8ba2\u5355\u6837\u672c\u3002 */
    static final Map<String, Object> ORDER = ImmutableMap.of("shopId", 7, "items", ImmutableList.of(ImmutableMap.of("skuCode", "S1", "quantity", 2)));
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    /** \u4fdd\u5b58\u5404\u8bf7\u6c42\u9000\u51fa\u540e\u4e0a\u4e0b\u6587\u6e05\u7406\u7ed3\u679c\u7684\u89c2\u6d4b\u961f\u5217\u3002 */
    static final ConcurrentLinkedQueue<Boolean> CLEAN = new ConcurrentLinkedQueue<>();
    /** \u7528\u4e8e\u7b49\u5f85\u6216\u89c2\u5bdf\u6d4b\u8bd5\u5de5\u4f5c\u7ed3\u675f\u7684\u540c\u6b65\u72b6\u6001\u3002 */
    static final ConcurrentLinkedQueue<Boolean> FINISHED = new ConcurrentLinkedQueue<>();
    /** \u6536\u96c6\u6267\u884c\u8bf7\u6c42\u6216\u56de\u8c03\u7684\u7ebf\u7a0b\u540d\u4ee5\u4fbf\u65ad\u8a00\u7ebf\u7a0b\u5207\u6362\u3002 */
    static final ConcurrentLinkedQueue<String> THREADS = new ConcurrentLinkedQueue<>();
    /** \u4fdd\u5b58\u7b49\u5f85\u6d4b\u8bd5\u91ca\u653e\u7684\u8bf7\u6c42\u6216\u5176\u540c\u6b65\u4fe1\u53f7\u3002 */
    static final String HELD_REQUEST = "r14-disconnect-satoken";
    /** \u7b49\u5f85\u88ab\u6302\u8d77\u8bf7\u6c42\u6700\u7ec8\u5b8c\u6210\u7684\u540c\u6b65\u4fe1\u53f7\u3002 */
    static final CompletableFuture<Boolean> HELD_FINISHED = new CompletableFuture<>();
    /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
    static final CountDownLatch ENTERED = new CountDownLatch(1), RELEASE = new CountDownLatch(1);

    /**
     * \u8c03\u7528 <code>CatalogRuntime.start()</code>\u3001<code>MYSQL.getDriverClassName()</code>\u3001<code>MYSQL.getJdbcUrl()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CatalogEndpoints</code> \u7ed3\u679c
     */
    static CatalogEndpoints catalog() {
        return CatalogRuntime.start(new UnpooledDataSource(MYSQL.getDriverClassName(),
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    /**
     * \u901a\u8fc7 <code>cn.dev33.satoken.SaManager.getSaTokenContext()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>cn.dev33.satoken.SaManager.getSaTokenContext().isValid()</code>
     */
    static boolean present() {
        return cn.dev33.satoken.SaManager.getSaTokenContext().isValid();
    }

    /**
     * \u8c03\u7528 <code>cn.hutool.core.util.ReflectUtil.getStaticFieldValue()</code>\u3001<code>cn.hutool.core.util.ReflectUtil.getField()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Object</code> \u7ed3\u679c
     */
    static Object nativeOwner() {
        return cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(cn.dev33.satoken.SaManager.class, "saTokenDao"));
    }

    /**
     * \u901a\u8fc7 <code>SampleHttpTransport.request()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param url \u72ec\u7acb\u6d4b\u8bd5\u670d\u52a1\u63a5\u6536\u8bf7\u6c42\u7684 URL
     * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>SampleHttpTransport.request(url, "GET", headers, Map.of("X-Tenant-Id", TENANT), null)</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static SampleHttpTransport.Response readOrder(String url, Map<String, String> headers)
            throws Exception {
        return SampleHttpTransport.request(url, "GET", headers, ImmutableMap.of("X-Tenant-Id", TENANT), null);
    }

    /**
     * \u6838\u5bf9<code>response.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>JSON.readTree(response.body()).path("data").asText()</code> \u7b49\u4e8e <code>"reader"</code>\u3002
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @param scheme \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws java.io.IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private static void assertReader(SampleHttpTransport.Response response, String scheme) throws java.io.IOException {
        assertEquals(200, response.statusCode(), scheme);
        assertEquals("reader", JSON.readTree(response.body()).path("data").asText(), scheme);
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
     * \u9a8c\u8bc1<code>driver.send("GET", "/auth/me", null, null).statusCode()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>driver.send("GET", ORDERS + "native-order", null, token).statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>FINISHED.contains(false)</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void actualNativeAuthenticationAndSharedOrder() throws Exception {
        io.ddd4j.core.subject.SubjectProvider previousProvider = io.ddd4j.core.util.SubjectKit.subjectProvider;
        Object previousNative = nativeOwner();
        Server server = Server.builder().port(0).addApplication(new HelidonSaTokenApplication(catalog()) {
            @Override
            public Set<Class<?>> getClasses() {
                java.util.HashSet<java.lang.Class<?>> classes = new HashSet<>(super.getClasses());
                classes.add(Observer.class);
                classes.add(Inner.class);
                return classes;
            }
        }).build().start();
        try {
            Driver driver = new Driver(server.port());
            assertEquals(401, driver.send("GET", "/auth/me", null, null).statusCode());
            assertEquals(401, driver.send("POST", "/auth/login", ImmutableMap.of("username", "alice", "password", "wrong"), null).statusCode());
            String token = driver.login("alice", "sample-password");
            driver.seedGoods(token);
            driver.createOrder("native-order", token);
            assertEquals(200, driver.send("POST", ORDERS + "native-order/payment", ImmutableMap.of("paidAmountCent", 1000), token).statusCode());
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

    /**
     * \u9a8c\u8bc1<code>driver.send("GET", order + "/no-route", null, null).statusCode()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>driver.send("GET", order, null, null).statusCode()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>allow</code>\u975e\u7a7a\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>driver.authenticatedPrincipal(writerToken)</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>driver.authenticatedPrincipal(token)</code> \u7b49\u4e8e <code>"reader"</code>\uff0c<code>readOrder(url, Map.of("Authorization", "BearerX " + token, tokenName, token)).statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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
            SampleHttpTransport.Response canonical = readOrder(url, ImmutableMap.of("Authorization", "Bearer " + token));
            assertEquals(200, canonical.statusCode());
            assertEquals(id, JSON.readTree(canonical.body()).path("orderNo").asText());
            String tokenName = StpUtil.getTokenName();
            String principalUrl = driver.base + "/auth/me";
            assertAll("authorization schemes for the same order and token",
                    () -> assertSameOrder(canonical, readOrder(url, ImmutableMap.of("Authorization", "bearer " + token)), "bearer"),
                    () -> assertSameOrder(canonical, readOrder(url, ImmutableMap.of("Authorization", "bEaReR " + token)), "bEaReR"),
                    () -> assertReader(readOrder(principalUrl, ImmutableMap.of("Authorization", "bearer " + token)), "bearer /auth/me"),
                    () -> assertReader(readOrder(principalUrl, ImmutableMap.of("Authorization", "bEaReR " + token)), "bEaReR /auth/me"),
                    () -> assertSameOrder(canonical, readOrder(url, ImmutableMap.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces"),
                    () -> assertSameOrder(canonical, readOrder(url, ImmutableMap.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces"),
                    () -> assertReader(readOrder(principalUrl, ImmutableMap.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces /auth/me"),
                    () -> assertReader(readOrder(principalUrl, ImmutableMap.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces /auth/me"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of("Authorization", "Bearer")).statusCode(),
                            "Bearer without token"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of("Authorization", "Bearer    ")).statusCode(),
                            "Bearer with only separator spaces"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of("Authorization", "Bearer   invalid-token")).statusCode(),
                            "invalid Bearer token"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of("Authorization", "Bearer   invalid-token",
                            tokenName, token)).statusCode(), "invalid Bearer with native token header"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of()).statusCode(), "without credentials"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of(tokenName, token)).statusCode(), "native token header alone"),
                    () -> assertEquals(401, readOrder(url, ImmutableMap.of("Authorization", "BearerX " + token,
                            tokenName, token)).statusCode(), "unsupported scheme with native token header"));
        } finally {
            server.stop();
        }
    }

    /** \u628a\u5171\u4eab\u9274\u6743\u65ad\u8a00\u8f6c\u53d1\u5230\u793a\u4f8b\u5e94\u7528\u7684\u8c03\u7528\u9a71\u52a8\u3002 */
    static class Driver implements AuthSampleDriver {
        /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
        final String base;
        /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
        final HttpClient client = HttpClient.newHttpClient();
        /** \u7528\u4e8e\u8986\u76d6\u7f3a\u5c11\u8ba2\u5355\u660e\u7ec6\u5206\u652f\u7684\u6d4b\u8bd5\u8ba2\u5355\u3002 */
        String emptyOrder;

        /**
         * \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>Driver</code> \u5b9e\u4f8b\u3002
         * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
         */
        Driver(int port) {
            base = "http://127.0.0.1:" + port;
        }

        /**
         * \u8c03\u7528 <code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code>\u3001<code>builder.header()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
         * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
         */
        HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", TENANT);
            if (Objects.nonNull(token)) {
                builder.header("Authorization", "Bearer " + token);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        /**
         * \u901a\u8fc7 <code>JSON.readTree()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>JSON.readTree(response.body()).path("data").asText()</code>
         */
        String request(String method, String path, Object body, String token) {
            try {
                HttpResponse<java.lang.String> response = send(method, path, body, token);
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

        /** {@inheritDoc} */
        @Override
        public String login(String user, String password) {
            try {
                return JSON.readTree(send("POST", "/auth/login", ImmutableMap.of("username", user, "password", password), null).body()).path("data").path("token").asText();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        /** {@inheritDoc} */
        @Override
        public String authenticatedPrincipal(String token) {
            return request("GET", "/auth/me", null, token);
        }

        /** {@inheritDoc} */
        @Override
        public String authorizedResource(String token, String permission) {
            return request("GET", "/auth/resource?permission=" + permission, null, token);
        }

        /**
         * \u8c03\u7528 <code>send()</code>\u3001<code>response.statusCode()</code>\u3001<code>response.body()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
         */
        void seedGoods(String token) throws Exception {
            HttpResponse<java.lang.String> response = send("POST", "/orders/catalog/goods/BOOK", ImmutableMap.of(
                    "spuName", "Book", "skuCode", "S1", "skuName", "Paperback", "shopId", 7, "salePriceCent", 500), token);
            assertTrue(response.statusCode() == 201 || response.statusCode() == 409, response.body());
        }

        /**
         * \u8c03\u7528 <code>send()</code>\u3001<code>response.statusCode()</code>\u3001<code>response.body()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
         */
        void createOrder(String id, String token) throws Exception {
            HttpResponse<java.lang.String> response = send("POST", ORDERS + id, ORDER, token);
            assertEquals(201, response.statusCode(), response.body());
        }

        /** {@inheritDoc} */
        @Override
        public void failingRequest(String token) {
            request("POST", ORDERS + emptyOrder + "/payment", ImmutableMap.of("paidAmountCent", 1), token);
        }

        /** {@inheritDoc} */
        @Override
        public void logout(String token) {
            request("POST", "/auth/logout", null, token);
        }

        /** {@inheritDoc} */
        @Override
        public Optional<String> contextPrincipal() {
            return CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty();
        }
    }

    /** \u5b9e\u73b0 <code>ContainerRequestFilter</code>\u3001<code>ContainerResponseFilter</code>\u3001<code>ApplicationEventListener</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>onEvent()</code>\u3001<code>onRequest()</code>\u3001<code>filter()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    @Provider
    @Priority(800)
    public static class Observer implements ContainerRequestFilter, ContainerResponseFilter, org.glassfish.jersey.server.monitoring.ApplicationEventListener {
        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @Override
        public void onEvent(org.glassfish.jersey.server.monitoring.ApplicationEvent event) {
        }

        /**
         * \u8c03\u7528 <code>finished.getType()</code>\u3001<code>present()</code>\u3001<code>io.ddd4j.core.context.ThreadContext.getSubject()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>org.glassfish.jersey.server.monitoring.RequestEventListener</code> \u7ed3\u679c
         */
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

        /**
         * \u8c03\u7528 <code>request.setProperty()</code>\u3001<code>Thread.currentThread()</code>\u3001<code>CLEAN.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         */
        @Override
        public void filter(ContainerRequestContext request) {
            request.setProperty("entry-thread", Thread.currentThread().getName());
            CLEAN.add(!present());
        }

        /**
         * \u8c03\u7528 <code>CLEAN.add()</code>\u3001<code>present()</code>\u3001<code>io.ddd4j.core.context.ThreadContext.getSubject()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
         */
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            CLEAN.add(!present() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
            THREADS.add(request.getProperty("entry-thread") + " -> " + Thread.currentThread().getName());
        }
    }

    /** \u5b9e\u73b0 <code>ContainerRequestFilter</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>filter()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    @Provider
    @Priority(1100)
    public static class Inner implements ContainerRequestFilter {
        /**
         * \u8c03\u7528 <code>present()</code>\u3001<code>request.getUriInfo()</code>\u3001<code>cn.dev33.satoken.stp.StpUtil.hasPermission()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         */
        @Override
        public void filter(ContainerRequestContext request) {
            assertTrue(present());
            if (!request.getUriInfo().getPath().equals("auth/login")) {
                assertEquals(StpUtil.hasPermission("order:read"), io.ddd4j.core.util.SubjectKit.getSubject().isPermitted("order:read"));
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
