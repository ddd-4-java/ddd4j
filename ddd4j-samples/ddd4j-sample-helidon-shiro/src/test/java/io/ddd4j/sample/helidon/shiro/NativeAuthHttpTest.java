package io.ddd4j.sample.helidon.shiro;

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
import org.apache.shiro.util.ThreadContext;
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
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 用例查询或写入数据时使用的租户标识。
     */
    static final String TENANT = "helidon-auth";
    /**
     * 用于区分订单路由、消息主题或投影事件流的测试名称。
     */
    static final String ORDERS = "/orders/catalog/orders/";
    /**
     * 当前合同或仓储操作所使用的订单样本。
     */
    static final Map<String, Object> ORDER = Map.of("shopId", 7, "items", java.util.List.of(Map.of("skuCode", "S1", "quantity", 2)));
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    /**
     * 保存各请求退出后上下文清理结果的观测队列。
     */
    static final ConcurrentLinkedQueue<Boolean> CLEAN = new ConcurrentLinkedQueue<>();
    /**
     * 用于等待或观察测试工作结束的同步状态。
     */
    static final ConcurrentLinkedQueue<Boolean> FINISHED = new ConcurrentLinkedQueue<>();
    /**
     * 收集执行请求或回调的线程名以便断言线程切换。
     */
    static final ConcurrentLinkedQueue<String> THREADS = new ConcurrentLinkedQueue<>();
    /**
     * 保存等待测试释放的请求或其同步信号。
     */
    static final String HELD_REQUEST = "r14-disconnect-shiro";
    /**
     * 等待被挂起请求最终完成的同步信号。
     */
    static final CompletableFuture<Boolean> HELD_FINISHED = new CompletableFuture<>();
    /**
     * 协调测试线程在指定执行阶段等待或继续的门闩。
     */
    static final CountDownLatch ENTERED = new CountDownLatch(1), RELEASE = new CountDownLatch(1);

    /**
     * 调用 <code>CatalogRuntime.start()</code>、<code>MYSQL.getDriverClassName()</code>、<code>MYSQL.getJdbcUrl()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>CatalogEndpoints</code> 结果
     */
    static CatalogEndpoints catalog() {
        return CatalogRuntime.start(new UnpooledDataSource(MYSQL.getDriverClassName(),
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    /**
     * 通过 <code>Objects.isNull()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>Objects.isNull(ThreadContext.getSubject()) &amp;&amp; Objects.isNull(ThreadContext.getSecurityManager())</code>
     */
    static boolean nativeBindingsCleared() {
        return Objects.isNull(ThreadContext.getSubject()) && Objects.isNull(ThreadContext.getSecurityManager());
    }

    /**
     * 调用 <code>cn.hutool.core.util.ReflectUtil.getStaticFieldValue()</code>、<code>cn.hutool.core.util.ReflectUtil.getField()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>Object</code> 结果
     */
    static Object nativeOwner() {
        return cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(org.apache.shiro.SecurityUtils.class, "securityManager"));
    }

    /**
     * 通过 <code>SampleHttpTransport.request()</code> 取得当前测试需要的结果。
     *
     * @param url     独立测试服务接收请求的 URL
     * @param headers 当前测试请求携带的头字段
     * @return 当前测试使用的结果，取自 <code>SampleHttpTransport.request(url, "GET", headers, Map.of("X-Tenant-Id", TENANT), null)</code>
     * @throws Exception 发起测试请求时失败
     */
    private static SampleHttpTransport.Response readOrder(String url, Map<String, String> headers)
            throws Exception {
        return SampleHttpTransport.request(url, "GET", headers, Map.of("X-Tenant-Id", TENANT), null);
    }

    /**
     * 核对<code>actual.statusCode()</code> 等于 <code>200</code>，<code>JSON.readTree(actual.body())</code> 等于 <code>JSON.readTree(canonical.body())</code>。
     *
     * @param canonical 传给 <code>assertEquals()</code> 的 <code>SampleHttpTransport.Response</code> 输入
     * @param actual    受测调用或测试环境实际产生的值
     * @param scheme    传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     * @throws java.io.IOException 读取或写入测试数据时发生 I/O 错误
     */
    private static void assertSameOrder(SampleHttpTransport.Response canonical, SampleHttpTransport.Response actual,
                                        String scheme) throws java.io.IOException {
        assertEquals(200, actual.statusCode(), scheme);
        assertEquals(JSON.readTree(canonical.body()), JSON.readTree(actual.body()), scheme);
    }

    /**
     * 验证<code>driver.send("GET", "/auth/me", null, null).statusCode()</code> 等于 <code>401</code>，<code>driver.send("GET", ORDERS + "native-order", null, token).statusCode()</code> 等于 <code>200</code>，<code>FINISHED.contains(false)</code>为假。
     *
     * @throws Exception 准备或读取夹具文件、等待并发操作、发起测试请求时失败
     */
    @Test
    void actualNativeAuthenticationAndSharedOrder() throws Exception {
        var previousProvider = io.ddd4j.core.util.SubjectKit.subjectProvider;
        Object previousNative = nativeOwner();
        Server server = Server.builder().port(0).addApplication(new HelidonShiroApplication(catalog()) {
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
            SampleContractAssertions.authentication("shiro", driver, "alice", "sample-password", "alice", "order:read", "order:cancel", "orders");
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
            System.out.println("S05 shiro server cleanup observations=" + CLEAN.size() + " enter/exit=" + THREADS);
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
     * 验证<code>driver.send("GET", order + "/no-route", null, null).statusCode()</code> 等于 <code>404</code>，<code>driver.send("GET", order, null, null).statusCode()</code> 等于 <code>401</code>，<code>allow</code>非空。
     *
     * @throws Exception 准备或读取夹具文件、处理测试数据的序列化、发起测试请求时失败
     */
    @Test
    void unsupportedOrderMethodPreservesNativeAllowHeader() throws Exception {
        Server server = Server.builder().port(0).addApplication(new HelidonShiroApplication(catalog())).build().start();
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
     * 验证<code>driver.authenticatedPrincipal(writerToken)</code> 等于 <code>"alice"</code>，<code>driver.authenticatedPrincipal(token)</code> 等于 <code>"reader"</code>，<code>readOrder(url, Map.of()).statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        Server server = Server.builder().port(0).addApplication(new HelidonShiroApplication(catalog())).build().start();
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
            assertAll("authorization schemes for the same order and token",
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bearer " + token)), "bearer"),
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bEaReR " + token)), "bEaReR"),
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces"),
                    () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer")).statusCode(),
                            "Bearer without token"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer    ")).statusCode(),
                            "Bearer with only separator spaces"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer   invalid-token")).statusCode(),
                            "invalid Bearer token"),
                    () -> assertEquals(401, readOrder(url, Map.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
                    () -> assertEquals(401, readOrder(url, Map.of()).statusCode(), "without credentials"));
        } finally {
            server.stop();
        }
    }

    /**
     * 把共享鉴权断言转发到示例应用的调用驱动。
     */
    static class Driver implements AuthSampleDriver {
        /**
         * 本地测试服务的基础地址。
         */
        final String base;
        /**
         * 发送与接收测试请求的客户端。
         */
        final HttpClient client = HttpClient.newHttpClient();
        /**
         * 用于覆盖缺少订单明细分支的测试订单。
         */
        String emptyOrder;

        /**
         * 构造供当前测试使用的 <code>Driver</code> 实例。
         *
         * @param port 测试服务监听的本地端口
         */
        Driver(int port) {
            base = "http://127.0.0.1:" + port;
        }

        /**
         * 调用 <code>HttpRequest.newBuilder()</code>、<code>URI.create()</code>、<code>builder.header()</code> 执行当前夹具操作。
         *
         * @param method 发给被测服务的 HTTP 方法
         * @param path   测试请求的资源路径或待访问文件路径
         * @param body   当前请求或响应中要读取的正文
         * @param token  从测试登录获得的会话凭据
         * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
         * @throws Exception 处理测试数据的序列化、发起测试请求时失败
         */
        HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", TENANT);
            if (Objects.nonNull(token)) {
                builder.header("Authorization", "Bearer " + token);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        /**
         * 通过 <code>JSON.readTree()</code> 取得当前测试需要的结果。
         *
         * @param method 发给被测服务的 HTTP 方法
         * @param path   测试请求的资源路径或待访问文件路径
         * @param body   当前请求或响应中要读取的正文
         * @param token  从测试登录获得的会话凭据
         * @return 当前测试使用的结果，取自 <code>JSON.readTree(response.body()).path("data").asText()</code>
         */
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

        /**
         * 以传入的凭据启动认证会话。
         *
         * <p>测试夹具会调用 <code>Map.of("username", user, "password", password)</code>。</p>
         *
         * @param user     传给 <code>send("POST", "/auth/login", Map.of("username", user, "password", password), null).body()</code>
         * @param password 已配置密码
         * @return 实际登录响应中的令牌
         */
        @Override
        public String login(String user, String password) {
            try {
                return JSON.readTree(send("POST", "/auth/login", Map.of("username", user, "password", password), null).body()).path("data").path("token").asText();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        /**
         * 通过已认证请求探测服务端识别出的登录主体。
         *
         * @param token 令牌
         * @return 实际返回<code>request("GET", "/auth/me", null, token)</code>
         */
        @Override
        public String authenticatedPrincipal(String token) {
            return request("GET", "/auth/me", null, token);
        }

        /**
         * 访问当前鉴权契约中受权限约束的资源。
         *
         * <p>测试夹具会调用 <code>request("GET", "/auth/resource?permission=" + permission, null, token)</code>。</p>
         *
         * @param token      令牌
         * @param permission 权限
         * @return 实际返回<code>request("GET", "/auth/resource?permission=" + permission, null, token)</code>
         */
        @Override
        public String authorizedResource(String token, String permission) {
            return request("GET", "/auth/resource?permission=" + permission, null, token);
        }

        /**
         * 调用 <code>send()</code>、<code>response.statusCode()</code>、<code>response.body()</code> 执行当前夹具操作。
         *
         * @param token 从测试登录获得的会话凭据
         * @throws Exception 当前用例准备资源或调用测试入口失败
         */
        void seedGoods(String token) throws Exception {
            var response = send("POST", "/orders/catalog/goods/BOOK", Map.of(
                    "spuName", "Book", "skuCode", "S1", "skuName", "Paperback", "shopId", 7, "salePriceCent", 500), token);
            assertTrue(response.statusCode() == 201 || response.statusCode() == 409, response.body());
        }

        /**
         * 调用 <code>send()</code>、<code>response.statusCode()</code>、<code>response.body()</code> 执行当前夹具操作。
         *
         * @param id    当前要查找的测试记录标识
         * @param token 从测试登录获得的会话凭据
         * @throws Exception 当前用例准备资源或调用测试入口失败
         */
        void createOrder(String id, String token) throws Exception {
            var response = send("POST", ORDERS + id, ORDER, token);
            assertEquals(201, response.statusCode(), response.body());
        }

        /**
         * 访问测试中指定会失败的请求路径。
         *
         * <p>测试夹具会调用 <code>Map.of("paidAmountCent", 1)</code>。</p>
         *
         * @param token 令牌；调用已注入业务异常的受保护入口并保留 IllegalStateException
         */
        @Override
        public void failingRequest(String token) {
            request("POST", ORDERS + emptyOrder + "/payment", Map.of("paidAmountCent", 1), token);
        }

        /**
         * 结束由当前调用指定的认证会话。
         *
         * <p>测试夹具会调用 <code>request("POST", "/auth/logout", null, token)</code>。</p>
         *
         * @param token 令牌；实际退出入口必须使该令牌失效
         */
        @Override
        public void logout(String token) {
            request("POST", "/auth/logout", null, token);
        }

        /**
         * 探测当前请求上下文里可读取的登录主体。
         *
         * <p>测试夹具会调用 <code>CLEAN.contains(false)</code>、<code>Optional.of("leaked")</code>。</p>
         *
         * @return 实际返回<code>CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty()</code>
         */
        @Override
        public Optional<String> contextPrincipal() {
            return CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty();
        }
    }

    /**
     * 实现 <code>ContainerRequestFilter</code>、<code>ContainerResponseFilter</code>、<code>ApplicationEventListener</code>，供测试调用 <code>onEvent()</code>、<code>onRequest()</code>、<code>filter()</code> 并观察结果。
     */
    @Provider
    @Priority(800)
    public static class Observer implements ContainerRequestFilter, ContainerResponseFilter, org.glassfish.jersey.server.monitoring.ApplicationEventListener {
        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @Override
        public void onEvent(org.glassfish.jersey.server.monitoring.ApplicationEvent event) {
        }

        /**
         * 调用 <code>finished.getType()</code>、<code>nativeBindingsCleared()</code>、<code>io.ddd4j.core.context.ThreadContext.getSubject()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         * @return 提供给后续契约断言的 <code>org.glassfish.jersey.server.monitoring.RequestEventListener</code> 结果
         */
        @Override
        public org.glassfish.jersey.server.monitoring.RequestEventListener onRequest(org.glassfish.jersey.server.monitoring.RequestEvent event) {
            return finished -> {
                if (finished.getType() == org.glassfish.jersey.server.monitoring.RequestEvent.Type.FINISHED) {
                    boolean clean = nativeBindingsCleared() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject());
                    FINISHED.add(clean);
                    if (HELD_REQUEST.equals(finished.getContainerRequest().getHeaderString("X-Test-Hold"))) {
                        HELD_FINISHED.complete(clean);
                    }
                }
            };
        }

        /**
         * 调用 <code>request.setProperty()</code>、<code>Thread.currentThread()</code>、<code>CLEAN.add()</code> 执行当前夹具操作。
         *
         * @param request 待交给被测服务处理的请求
         */
        @Override
        public void filter(ContainerRequestContext request) {
            request.setProperty("entry-thread", Thread.currentThread().getName());
            CLEAN.add(nativeBindingsCleared());
        }

        /**
         * 调用 <code>CLEAN.add()</code>、<code>nativeBindingsCleared()</code>、<code>io.ddd4j.core.context.ThreadContext.getSubject()</code> 执行当前夹具操作。
         *
         * @param request  待交给被测服务处理的请求
         * @param response 当前请求已收到的响应
         */
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            CLEAN.add(nativeBindingsCleared() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
            THREADS.add(request.getProperty("entry-thread") + " -> " + Thread.currentThread().getName());
        }
    }

    /**
     * 实现 <code>ContainerRequestFilter</code>，供测试调用 <code>filter()</code> 并观察结果。
     */
    @Provider
    @Priority(1100)
    public static class Inner implements ContainerRequestFilter {
        /**
         * 调用 <code>ThreadContext.getSubject()</code>、<code>ThreadContext.getSecurityManager()</code>、<code>request.getUriInfo()</code> 执行当前夹具操作。
         *
         * @param request 待交给被测服务处理的请求
         */
        @Override
        public void filter(ContainerRequestContext request) {
            assertNotNull(ThreadContext.getSubject());
            assertNotNull(ThreadContext.getSecurityManager());
            if (!request.getUriInfo().getPath().equals("auth/login")) {
                assertEquals(org.apache.shiro.SecurityUtils.getSubject().isPermitted("order:read"), io.ddd4j.core.util.SubjectKit.getSubject().isPermitted("order:read"));
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
