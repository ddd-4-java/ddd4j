package io.ddd4j.sample.micronaut.shiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 原生 HTTP/会话/订单验证；观测器只读线程状态，绝不清理所测状态。
 */
@Testcontainers
class NativeAuthHttpTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    /**
     * 保存各请求退出后上下文清理结果的观测队列。
     */
    static final ConcurrentLinkedQueue<Boolean> CLEAN = new ConcurrentLinkedQueue<>();
    /**
     * 收集执行请求或回调的线程名以便断言线程切换。
     */
    static final ConcurrentLinkedQueue<String> THREADS = new ConcurrentLinkedQueue<>();
    /**
     * 用于等待或观察测试工作结束的同步状态。
     */
    static final ConcurrentLinkedQueue<String> FINISHED = new ConcurrentLinkedQueue<>();
    /**
     * 等待被测操作已进入执行的同步门闩。
     */
    static volatile CountDownLatch entered = new CountDownLatch(1);
    /**
     * 用来控制测试线程退出等待或观察操作完成的同步门闩。
     */
    static volatile CountDownLatch release = new CountDownLatch(1);

    /**
     * 调用 <code>ApplicationContext.run()</code>、<code>MYSQL.getJdbcUrl()</code>、<code>MYSQL.getUsername()</code> 执行当前夹具操作。
     *
     * @param observe 传给 <code>ApplicationContext.run()</code> 的 <code>boolean</code> 输入
     * @return 提供给后续契约断言的 <code>EmbeddedServer</code> 结果
     */
    static EmbeddedServer start(boolean observe) {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "sample.test.observer", observe, "catalog.jdbc.url", MYSQL.getJdbcUrl(),
                "catalog.db.user", MYSQL.getUsername(), "catalog.db.password", MYSQL.getPassword(),
                "ddd4j.web.idempotency-enabled", false));
    }

    /**
     * 通过 <code>Objects.nonNull()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>Objects.nonNull(org.apache.shiro.util.ThreadContext.getSubject())</code>
     */
    static boolean nativePresent() {
        return Objects.nonNull(org.apache.shiro.util.ThreadContext.getSubject());
    }

    /**
     * 通过 <code>org.apache.shiro.util.ThreadContext.getSubject()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>org.apache.shiro.util.ThreadContext.getSubject()</code>
     */
    static Object nativeState() {
        return org.apache.shiro.util.ThreadContext.getSubject();
    }

    /**
     * 通过 <code>SampleHttpTransport.request()</code> 取得当前测试需要的结果。
     *
     * @param url      独立测试服务接收请求的 URL
     * @param tenantId 测试数据所属的租户标识
     * @param headers  当前测试请求携带的头字段
     * @return 当前测试使用的结果，取自 <code>SampleHttpTransport.request(url, "GET", requestHeaders, Map.of(), null)</code>
     * @throws java.io.IOException 读取或写入测试数据时发生 I/O 错误
     */
    private static SampleHttpTransport.Response readOrder(String url, String tenantId, Map<String, String> headers)
            throws java.io.IOException {
        var requestHeaders = new java.util.HashMap<>(headers);
        requestHeaders.put("X-Tenant-Id", tenantId);
        return SampleHttpTransport.request(url, "GET", requestHeaders, Map.of(), null);
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
     * 核对<code>driver.find(id).path("orderState").asText()</code> 等于 <code>state</code>。
     *
     * @param driver 传给 <code>assertEquals()</code> 的 <code>Driver</code> 输入
     * @param id     当前要查找的测试记录标识
     * @param token  从测试登录获得的会话凭据
     * @param suffix 传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     * @param status 当前 HTTP 或业务流程的状态
     * @param state  传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     * @throws Exception 发起测试请求时失败
     */
    private static void assertCancellation(Driver driver, String id, String token, String suffix,
                                           int status, String state) throws Exception {
        assertEquals(status, driver.send("POST", "/orders/catalog/orders/" + id + "/cancel" + suffix, null, token).statusCode());
        assertEquals(state, driver.find(id).path("orderState").asText());
    }

    /**
     * 核对<code>manager.getRealms()</code>、<code>manager.setRealm()</code>、<code>driver.login()</code> 返回的测试结果。
     *
     * @param driver     传给 <code>driver.login()</code> 的 <code>Driver</code> 输入
     * @param id         当前要查找的测试记录标识
     * @param slashOrder 传给 <code>assertCancellation()</code> 的 <code>String</code> 输入
     * @param manager    传给 <code>manager.getRealms()</code> 的 <code>DefaultSecurityManager</code> 输入
     * @throws Exception 发起测试请求时失败
     */
    private static void assertPermittedCancellation(Driver driver, String id, String slashOrder,
                                                    DefaultSecurityManager manager) throws Exception {
        Collection<Realm> previous = manager.getRealms();
        manager.setRealm(new CancellationRealm());
        try {
            String token = driver.login("canceller", "sample-password");
            assertEquals(403, driver.send("POST", "/orders/catalog/orders/no-write", Map.of("shopId", 7), token).statusCode());
            assertCancellation(driver, id, token, "", 200, "CANCELLED");
            assertCancellation(driver, slashOrder, token, "/", 200, "CANCELLED");
            driver.logout(token);
        } finally {
            manager.setRealms(previous);
        }
    }

    /**
     * 验证<code>driver.send("GET", order + "/no-route", null, null).statusCode()</code> 等于 <code>404</code>，<code>driver.send("GET", order + "/payment", null, null).statusCode()</code> 等于 <code>405</code>。
     *
     * @throws Exception 发起测试请求时失败
     */
    @Test
    void unmatchedRoutesKeepNativeHttpStatus() throws Exception {
        try (EmbeddedServer server = start(false)) {
            Driver driver = new Driver(server.getPort());
            String order = "/orders/catalog/orders/parity-probe";
            assertAll("the router owns unmatched requests",
                    () -> assertEquals(404, driver.send("GET", order + "/no-route", null, null).statusCode()),
                    () -> assertEquals(405, driver.send("GET", order + "/payment", null, null).statusCode()));
        }
    }

    /**
     * 验证<code>driver.authenticatedPrincipal(readerToken)</code> 等于 <code>"reader"</code>，<code>driver.authenticatedPrincipal(writerToken)</code> 等于 <code>"alice"</code>，<code>writer</code> 等于 <code>400</code>。
     *
     * @throws Exception 发起测试请求时失败
     */
    @Test
    void authorizationPrecedesRequiredPaymentParameter() throws Exception {
        try (EmbeddedServer server = start(false)) {
            Driver driver = new Driver(server.getPort());
            String payment = "/orders/catalog/orders/parity-probe/payment";
            int anonymous = driver.send("POST", payment, null, null).statusCode();
            String readerToken = driver.login("reader", "sample-password");
            assertEquals("reader", driver.authenticatedPrincipal(readerToken));
            int reader = driver.send("POST", payment, null, readerToken).statusCode();
            String writerToken = driver.login("alice", "sample-password");
            assertEquals("alice", driver.authenticatedPrincipal(writerToken));
            int writer = driver.send("POST", payment, null, writerToken).statusCode();
            assertAll("payment with no key",
                    () -> assertEquals(401, anonymous, "anonymous must be challenged before binding"),
                    () -> assertEquals(403, reader, "read-only user must be denied before binding"),
                    () -> assertEquals(400, writer, "authorized writer reaches required parameter validation"));
        }
    }

    /**
     * 验证<code>driver.authenticatedPrincipal(writerToken)</code> 等于 <code>"alice"</code>，<code>driver.authenticatedPrincipal(token)</code> 等于 <code>"reader"</code>，<code>readOrder(url, driver.tenantId, Map.of()).statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
        try (EmbeddedServer server = start(false)) {
            Driver driver = new Driver(server.getPort());
            String writerToken = driver.login("alice", "sample-password");
            assertEquals("alice", driver.authenticatedPrincipal(writerToken));
            String id = driver.create("bearer-scheme");
            String token = driver.login("reader", "sample-password");
            assertEquals("reader", driver.authenticatedPrincipal(token));
            String url = "http://127.0.0.1:" + server.getPort() + "/orders/catalog/orders/" + id;
            SampleHttpTransport.Response canonical = readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer " + token));
            assertEquals(200, canonical.statusCode());
            assertEquals(id, JSON.readTree(canonical.body()).path("orderNo").asText());
            assertAll("authorization schemes for the same order and token",
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "bearer " + token)), "bearer"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "bEaReR " + token)), "bEaReR"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer  " + token)),
                            "Bearer with two separator spaces"),
                    () -> assertSameOrder(canonical, readOrder(url, driver.tenantId, Map.of("Authorization", "bEaReR    " + token)),
                            "mixed-case Bearer with four separator spaces"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer")).statusCode(),
                            "Bearer without token"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer    ")).statusCode(),
                            "Bearer with only separator spaces"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "Bearer   invalid-token")).statusCode(),
                            "invalid Bearer token"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
                    () -> assertEquals(401, readOrder(url, driver.tenantId, Map.of()).statusCode(), "without credentials"));
        }
    }

    /**
     * 验证<code>driver.send("GET", "/auth/me", null, null).statusCode()</code> 等于 <code>401</code>，<code>driver.authenticatedPrincipal(token)</code> 等于 <code>"alice"</code>，<code>io.ddd4j.core.util.SubjectKit.getDataProvider()</code> 与…为同一实例 <code>previousData</code>。
     *
     * @throws Exception 准备或读取夹具文件、等待并发操作、发起测试请求时失败
     */
    @Test
    void nativeLoginAndOrderAuthorization() throws Exception {
        CLEAN.clear();
        THREADS.clear();
        FINISHED.clear();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        Object previousData = io.ddd4j.core.util.SubjectKit.getDataProvider();
        Object previous = cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(org.apache.shiro.SecurityUtils.class, "securityManager"));
        try (EmbeddedServer server = start(true)) {
            assertSame(previous, cn.hutool.core.util.ReflectUtil.getStaticFieldValue(
                    cn.hutool.core.util.ReflectUtil.getField(SecurityUtils.class, "securityManager")));
            NativeAuth auth = server.getApplicationContext().getBean(NativeAuth.class);
            Driver driver = new Driver(server.getPort());
            assertEquals(401, driver.send("GET", "/auth/me", null, null).statusCode());
            assertEquals(401, driver.send("POST", "/auth/login", Map.of("username", "alice", "password", "wrong"), null).statusCode());
            String token = driver.login("alice", "sample-password");
            driver.token = token;
            String id = driver.create("shiro-native-order");
            assertEquals("alice", driver.authenticatedPrincipal(token));
            assertEquals("AWAITING_PAYMENT", driver.find(id).path("orderState").asText());
            driver.logout(token);
            String loggedOut = token;
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal(loggedOut));
            // 同步请求在连接断开后仍按 finally 结束；不声称支持任意 Publisher/线程迁移。
            token = driver.login("alice", "sample-password");
            String slashOrder = driver.create("shiro-permission-slash");
            assertCancellation(driver, id, token, "", 403, "AWAITING_PAYMENT");
            assertCancellation(driver, slashOrder, token, "/", 403, "AWAITING_PAYMENT");
            assertPermittedCancellation(driver, id, slashOrder, (DefaultSecurityManager) auth.manager());
            token = driver.login("alice", "sample-password");
            String observation = java.util.UUID.randomUUID().toString();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                String request = "GET /auth/me HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + token + "\r\nX-Test-Hold: " + observation + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTrue(entered.await(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!FINISHED.contains(observation) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(FINISHED.contains(observation), "the exact disconnected route completed native cleanup");
            assertFalse(CLEAN.contains(false), "native and ddd4j state clean on actual server thread");
            assertFalse(THREADS.isEmpty());
            System.out.println("S04 shiro server lifecycle observations=" + CLEAN.size() + " threads=" + new java.util.TreeSet<>(THREADS));
        }
        assertFalse(CLEAN.contains(false), "all server-thread restoration observations remain clean after Context closes");
        assertSame(previousData, io.ddd4j.core.util.SubjectKit.getDataProvider());
        assertSame(previous, cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(org.apache.shiro.SecurityUtils.class, "securityManager")), "context close restores prior native global owner");
    }

    /**
     * 用于验证请求取消及线程清理的 Shiro Realm。
     */
    private static final class CancellationRealm extends SimpleAccountRealm {
        /**
         * 构造供当前测试使用的 <code>CancellationRealm</code> 实例。
         */
        CancellationRealm() {
            SimpleAccount account = new SimpleAccount("canceller", "sample-password", getName());
            account.addStringPermission("order:read");
            account.addStringPermission("order:cancel");
            add(account);
        }
    }

    /**
     * 将 <code>base</code>、<code>client</code>、<code>tenantId</code>、<code>token</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    static class Driver {
        /**
         * 本地测试服务的基础地址。
         */
        final String base;
        /**
         * 发送与接收测试请求的客户端。
         */
        final HttpClient client = HttpClient.newHttpClient();
        /**
         * 当前业务数据所属的租户标识。
         */
        final String tenantId;
        /**
         * 请求认证测试中使用的会话令牌。
         */
        String token;

        /**
         * 构造供当前测试使用的 <code>Driver</code> 实例。
         *
         * @param port 测试服务监听的本地端口
         */
        Driver(int port) {
            base = "http://127.0.0.1:" + port;
            tenantId = java.util.UUID.randomUUID().toString().substring(0, 20);
        }

        /**
         * 调用 <code>HttpRequest.newBuilder()</code>、<code>URI.create()</code>、<code>builder.header()</code> 执行当前夹具操作。
         *
         * @param method 发给被测服务的 HTTP 方法
         * @param path   测试请求的资源路径或待访问文件路径
         * @param body   当前请求或响应中要读取的正文
         * @param bearer 传给 <code>Objects.nonNull()</code> 的 <code>String</code> 输入
         * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
         * @throws Exception 处理测试数据的序列化、发起测试请求时失败
         */
        HttpResponse<String> send(String method, String path, Object body, String bearer) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenantId);
            if ("POST".equals(method) && path.endsWith("/payment") && Objects.nonNull(body)) {
                builder.header("Idempotency-Key", "checkout-confirmation");
            }
            if (Objects.nonNull(bearer)) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            return client.send(builder.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        /**
         * 按 <code>(response.statusCode() == 401 || response.statusCode() == 403)</code> 等实际条件选择当前用例返回的结果。
         *
         * @param <T>    当前契约测试所使用的 Java 类型
         * @param method 发给被测服务的 HTTP 方法
         * @param path   测试请求的资源路径或待访问文件路径
         * @param body   当前请求或响应中要读取的正文
         * @param type   用来解析、检查或构造测试对象的 Java 类型
         * @return 根据测试条件返回的可能值，包括 <code>type.cast(response.body().startsWith("\"") ? JSON.readValue(response.body(), String.class) : response.body())</code>、<code>JSON.readValue(response.body(), type)</code>
         */
        public <T> T request(String method, String path, Object body, Class<T> type) {
            try {
                var response = send(method, path, body, token);
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new SecurityException(response.body());
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
                }
                if (type == String.class) {
                    return type.cast(response.body().startsWith("\"") ? JSON.readValue(response.body(), String.class) : response.body());
                }
                return JSON.readValue(response.body(), type);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(exception);
            }
        }

        /**
         * 读取请求认证测试中使用的会话令牌。
         *
         * @param user     传给 <code>request()</code> 的 <code>String</code> 输入
         * @param password 测试账号的口令
         * @return 当前测试使用的结果，取自 <code>token</code>
         */
        public String login(String user, String password) {
            token = null;
            token = request("POST", "/auth/login", Map.of("username", user, "password", password), com.fasterxml.jackson.databind.JsonNode.class).path("token").asText();
            return token;
        }

        /**
         * 通过 <code>request()</code> 取得当前测试需要的结果。
         *
         * @param token 从测试登录获得的会话凭据
         * @return 当前测试使用的结果，取自 <code>request("GET", "/auth/me", null, com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText()</code>
         */
        public String authenticatedPrincipal(String token) {
            this.token = token;
            return request("GET", "/auth/me", null, com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText();
        }

        /**
         * 读取 <code>orderNo</code> 供当前测试使用。
         *
         * @param orderNo 测试中要定位的租户内订单号
         * @return 当前测试使用的结果，取自 <code>orderNo</code>
         * @throws Exception 当前用例准备资源或调用测试入口失败
         */
        String create(String orderNo) throws Exception {
            var goods = send("POST", "/orders/catalog/goods/" + orderNo,
                    Map.of("spuName", "Demo", "skuCode", orderNo, "skuName", "Standard", "salePriceCent", 1250, "shopId", 7), token);
            assertEquals(201, goods.statusCode(), goods.body());
            var created = send("POST", "/orders/catalog/orders/" + orderNo,
                    Map.of("shopId", 7, "items", List.of(Map.of("skuCode", orderNo, "quantity", 1))), token);
            assertEquals(201, created.statusCode(), created.body());
            return orderNo;
        }

        /**
         * 通过 <code>request()</code> 取得当前测试需要的结果。
         *
         * @param orderNo 测试中要定位的租户内订单号
         * @return 当前测试使用的结果，取自 <code>request("GET", "/orders/catalog/orders/" + orderNo, null, com.fasterxml.jackson.databind.JsonNode.class)</code>
         */
        com.fasterxml.jackson.databind.JsonNode find(String orderNo) {
            return request("GET", "/orders/catalog/orders/" + orderNo, null, com.fasterxml.jackson.databind.JsonNode.class);
        }

        /**
         * 根据本次调用更新 <code>this.token = token</code>，用于后续测试步骤。
         *
         * @param token 从测试登录获得的会话凭据
         */
        public void logout(String token) {
            this.token = token;
            request("POST", "/auth/logout", null, com.fasterxml.jackson.databind.JsonNode.class);
        }

        /**
         * 通过 <code>CLEAN.contains()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty()</code>
         */
        public Optional<String> contextPrincipal() {
            return CLEAN.contains(false) ? Optional.of("leaked") : Optional.empty();
        }
    }

    /**
     * 提供 <code>observe()</code> 等供当前测试使用的独立操作。
     */
    @ServerFilter("/**")
    @Order(-200)
    @Requires(property = "sample.test.observer", value = "true")
    static class OuterObserver {
        /**
         * 通过 <code>next.request()</code> 取得当前测试需要的结果。
         *
         * @param request    待交给被测服务处理的请求
         * @param next       传给 <code>next.request()</code> 的 <code>FilterContinuation&lt;MutableHttpResponse&lt;?&gt;&gt;</code> 输入
         * @param propagated 传给 <code>propagated.add()</code> 的 <code>io.micronaut.core.propagation.MutablePropagatedContext</code> 输入
         * @return 当前测试使用的结果，取自 <code>next.request(request).proceed()</code>
         */
        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next, io.micronaut.core.propagation.MutablePropagatedContext propagated) {
            String observation = request.getHeaders().get("X-Test-Hold");
            propagated.add(new io.micronaut.core.propagation.ThreadPropagatedContextElement<Object>() {
                @Override
                public Object updateThreadContext() {
                    return nativeState();
                }

                @Override
                public void restoreThreadContext(Object previous) {
                    CLEAN.add(previous == nativeState());
                    THREADS.add("restored:" + Thread.currentThread().getName());
                }
            });
            try {
                return next.request(request).proceed();
            } finally {
                CLEAN.add(!nativePresent() && Objects.isNull(org.apache.shiro.util.ThreadContext.getSecurityManager())
                        && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
                THREADS.add(Thread.currentThread().getName());
                if (Objects.nonNull(observation)) {
                    FINISHED.add(observation);
                }
            }
        }
    }

    /**
     * 将 <code>auth</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    @ServerFilter("/**")
    @Order(-50)
    @Requires(property = "sample.test.observer", value = "true")
    static class InnerObserver {
        /**
         * 对当前测试请求执行原生认证的组件。
         */
        private final NativeAuth auth;

        /**
         * 设置 <code>auth</code>，用于构造 <code>InnerObserver</code> 测试实例。
         *
         * @param auth 用于初始化 <code>InnerObserver</code> 的 <code>NativeAuth</code> 值
         */
        InnerObserver(NativeAuth auth) {
            this.auth = auth;
        }

        /**
         * 通过 <code>next.proceed()</code> 取得当前测试需要的结果。
         *
         * @param request 待交给被测服务处理的请求
         * @param next    传给 <code>next.proceed()</code> 的 <code>FilterContinuation&lt;MutableHttpResponse&lt;?&gt;&gt;</code> 输入
         * @return 当前测试使用的结果，取自 <code>next.proceed()</code>
         * @throws InterruptedException 等待测试操作时被中断
         */
        @RequestFilter
        MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next) throws InterruptedException {
            assertTrue(nativePresent(), "actual native context exists inside filter scope");
            assertSame(auth.manager(), SecurityUtils.getSecurityManager());
            request.setAttribute("observed-thread", Thread.currentThread().getName());
            if (request.getHeaders().contains("X-Test-Hold")) {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return next.proceed();
        }
    }
}
