package io.ddd4j.sample.spring.satoken;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.RequestContextHolder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 原生引擎通过真实 Servlet/HTTP/DI 执行鉴权；外层测试过滤器只观察，不清理上下文。
 */
@Testcontainers
class SpringSatokenHttpTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 核对<code>driver.call(method, path, token, body)</code> 抛出 <code>SecurityException.class</code>，<code>driver.lastStatus</code> 等于 <code>403</code>。
     *
     * @param driver 传给 <code>assertThrows()</code> 的 <code>AuthDriver</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param token  从测试登录获得的会话凭据
     * @param body   当前请求或响应中要读取的正文
     */
    private static void assertForbidden(AuthDriver driver, String method, String path, String token, String body) {
        try {
            assertThrows(SecurityException.class, () -> driver.call(method, path, token, body));
            assertEquals(403, driver.lastStatus);
        } finally {
            assertEquals(Optional.empty(), driver.contextPrincipal(), "denied request server cleanup");
        }
    }

    /**
     * 验证 Sa-Token 在根路径下的真实 HTTP 认证及请求结束后的上下文清理。
     */
    @Test
    void nativeAuthAndRequestCleanup() {
        verify("");
    }

    /**
     * 验证 Sa-Token 在配置的上下文路径下仍完成认证和请求清理。
     */
    @Test
    void nativeAuthWithContextPathAndRequestCleanup() {
        verify("/sample");
    }

    /**
     * 调用 <code>SpringApplication.run()</code>、<code>MYSQL.getJdbcUrl()</code>、<code>MYSQL.getUsername()</code> 执行当前夹具操作。
     *
     * @param contextPath 传给 <code>SpringApplication.run()</code> 的 <code>String</code> 输入
     */
    private void verify(String contextPath) {
        var context = SpringApplication.run(SpringSatokenSampleApplication.class, "--server.port=0", "--server.tomcat.threads.max=1", "--server.tomcat.threads.min-spare=1",
                "--server.servlet.context-path=" + contextPath, "--CATALOG_JDBC_URL=" + MYSQL.getJdbcUrl(),
                "--CATALOG_DB_USER=" + MYSQL.getUsername(), "--CATALOG_DB_PASSWORD=" + MYSQL.getPassword());
        try (context) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var observations = context.getBean(Observation.class);
            AuthDriver driver = new AuthDriver("http://127.0.0.1:" + port + contextPath, observations);
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal("invalid"));
            driver.contextPrincipal();
            assertThrows(SecurityException.class, () -> driver.login("alice", "wrong"));
            driver.contextPrincipal();
            String writer = driver.login("writer", "demo-password");
            assertEquals(Optional.empty(), driver.contextPrincipal());
            assertEquals("writer", driver.authenticatedPrincipal(writer));
            assertEquals(Optional.empty(), driver.contextPrincipal());
            driver.call("POST", "/orders/catalog/goods/LUNCH", writer, """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """);
            assertEquals(201, driver.lastStatus);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            driver.call("POST", "/orders/catalog/orders/T001", writer, """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """);
            assertEquals(201, driver.lastStatus);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            driver.logout(writer);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal(writer));
            assertEquals(Optional.empty(), driver.contextPrincipal());

            String reader = driver.login("alice", "demo-password");
            assertEquals(Optional.empty(), driver.contextPrincipal());
            assertForbidden(driver, "POST", "/orders/catalog/orders/T001/payment", reader, "{\"paidAmountCent\":2500}");
            String order = driver.call("GET", "/orders/catalog/orders/T001", reader, "");
            org.junit.jupiter.api.Assertions.assertTrue(order.contains("\"grossAmountCent\":2500"), order);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            assertThrows(IllegalStateException.class, () -> driver.call("GET", "/orders/catalog/orders/missing", reader, ""));
            assertEquals(404, driver.lastStatus);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            driver.logout(reader);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            org.junit.jupiter.api.Assertions.assertEquals(1, observations.workers.size(), "same actual worker reused");
        }
        assertFalse(context.isActive());
    }

    /**
     * 向测试容器注册 <code>requestObservation()</code> 等测试入口或组件。
     */
    @Configuration(proxyBeanMethods = false)
    static class Observation {
        /**
         * 执行测试工作任务的线程或执行组件。
         */
        final java.util.Set<Long> workers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        /**
         * 当前测试记录的属性值或批量输入集合。
         */
        final LinkedBlockingQueue<Optional<String>> values = new LinkedBlockingQueue<>();

        /**
         * 读取 <code>registration</code> 供当前测试使用。
         *
         * @return 当前测试使用的结果，取自 <code>registration</code>
         */
        @Bean
        FilterRegistrationBean<Filter> requestObservation() {
            Filter filter = (request, response, chain) -> {
                try {
                    chain.doFilter(request, response);
                } finally {
                    Object nativeContext = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                    Object requestContext = RequestContextHolder.getRequestAttributes();
                    workers.add(Thread.currentThread().getId());
                    boolean ddd4jBound = !io.ddd4j.core.context.ThreadContext.getResources().isEmpty()
                            || !io.ddd4j.core.context.ThreadContext.getValues().isEmpty();
                    boolean nativeBound = cn.dev33.satoken.SaManager.getSaTokenContext().isValid();
                    values.add(nativeBound || ddd4jBound || Objects.nonNull(requestContext)
                            ? Optional.of("leaked-native-or-request-context") : Optional.empty());
                }
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    /**
     * 将 <code>base</code>、<code>observation</code>、<code>tenant</code>、<code>client</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static class AuthDriver {
        /**
         * 本地测试服务的基础地址。
         */
        private final String base;
        /**
         * 收集到的框架事件、调用或 SQL 观测结果。
         */
        private final Observation observation;
        /**
         * 业务数据或认证请求所属的测试租户。
         */
        private final String tenant = java.util.UUID.randomUUID().toString().substring(0, 20);
        /**
         * 发送与接收测试请求的客户端。
         */
        private final HttpClient client = HttpClient.newHttpClient();
        /**
         * 保存当前尚未执行或尚未处理完成的测试操作。
         */
        private int pending;
        /**
         * 最近一次记录的响应或投递状态。
         */
        private int lastStatus;

        /**
         * 用给定值初始化 <code>AuthDriver</code> 实例。
         *
         * @param base        本地测试服务的基础地址
         * @param observation <code>observation</code> 字段
         */
        AuthDriver(String base, Observation observation) {
            this.base = base;
            this.observation = observation;
        }

        /**
         * 调用 <code>call()</code> 执行当前夹具操作。
         *
         * @param username 测试会话使用的用户名
         * @param password 测试账号的口令
         * @return 提供给后续契约断言的 <code>String</code> 结果
         */
        public String login(String username, String password) {
            return call("POST", "/auth/login?username=" + username + "&password=" + password, "", "");
        }

        /**
         * 通过 <code>call()</code> 取得当前测试需要的结果。
         *
         * @param token 从测试登录获得的会话凭据
         * @return 当前测试使用的结果，取自 <code>call("GET", "/auth/me", token, "")</code>
         */
        public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, "");
        }

        /**
         * 调用 <code>call()</code> 执行当前夹具操作。
         *
         * @param token 从测试登录获得的会话凭据
         */
        public void logout(String token) {
            call("POST", "/auth/logout", token, "");
        }

        /**
         * 按 <code>(pending == 0)</code> 等实际条件选择当前用例返回的结果。
         *
         * @return 根据测试条件返回的可能值，包括 <code>Optional.ofNullable(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).map(Object::toString)</code>、<code>Objects.requireNonNull(observation.values.poll(10, TimeUnit.SECONDS), "request observation missing")</code>
         */
        public Optional<String> contextPrincipal() {
            if (pending == 0) {
                return Optional.ofNullable(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).map(Object::toString);
            }
            pending--;
            try {
                return Objects.requireNonNull(observation.values.poll(10, TimeUnit.SECONDS), "request observation missing");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        /**
         * 根据本次调用更新 <code>lastStatus = response.statusCode()</code>，用于后续测试步骤。
         *
         * @param method 发给被测服务的 HTTP 方法
         * @param path   测试请求的资源路径或待访问文件路径
         * @param token  从测试登录获得的会话凭据
         * @param body   当前请求或响应中要读取的正文
         * @return 该异常路径没有正常返回结果
         */
        private String call(String method, String path, String token, String body) {
            try {
                var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
                if (!token.isEmpty()) {
                    builder.header("satoken", token);
                }
                pending++;
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                lastStatus = response.statusCode();
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new SecurityException(response.body());
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                }
                return response.body().startsWith("\"")
                        ? new com.fasterxml.jackson.databind.ObjectMapper().readValue(response.body(), String.class) : response.body();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }
}
