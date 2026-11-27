package io.ddd4j.sample.spring.satoken;

import javax.servlet.Filter;
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
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
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
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u6838\u5bf9<code>driver.call(method, path, token, body)</code> \u629b\u51fa <code>SecurityException.class</code>\uff0c<code>driver.lastStatus</code> \u7b49\u4e8e <code>403</code>\u3002
     * @param driver \u4f20\u7ed9 <code>assertThrows()</code> \u7684 <code>AuthDriver</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     */
    private static void assertForbidden(AuthDriver driver, String method, String path, String token, String body) {
        try {
            assertThrows(SecurityException.class, () -> driver.call(method, path, token, body));
            assertEquals(403, driver.lastStatus);
        } finally {
            assertEquals(Optional.empty(), driver.contextPrincipal(), "denied request server cleanup");
        }
    }

    /** \u9a8c\u8bc1 Sa-Token \u5728\u6839\u8def\u5f84\u4e0b\u7684\u771f\u5b9e HTTP \u8ba4\u8bc1\u53ca\u8bf7\u6c42\u7ed3\u675f\u540e\u7684\u4e0a\u4e0b\u6587\u6e05\u7406\u3002 */
    @Test
    void nativeAuthAndRequestCleanup() {
        verify("");
    }

    /** \u9a8c\u8bc1 Sa-Token \u5728\u914d\u7f6e\u7684\u4e0a\u4e0b\u6587\u8def\u5f84\u4e0b\u4ecd\u5b8c\u6210\u8ba4\u8bc1\u548c\u8bf7\u6c42\u6e05\u7406\u3002 */
    @Test
    void nativeAuthWithContextPathAndRequestCleanup() {
        verify("/sample");
    }

    /**
     * \u8c03\u7528 <code>SpringApplication.run()</code>\u3001<code>MYSQL.getJdbcUrl()</code>\u3001<code>MYSQL.getUsername()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param contextPath \u4f20\u7ed9 <code>SpringApplication.run()</code> \u7684 <code>String</code> \u8f93\u5165
     */
    private void verify(String contextPath) {
        org.springframework.context.ConfigurableApplicationContext context = SpringApplication.run(SpringSatokenSampleApplication.class, "--server.port=0", "--server.tomcat.threads.max=1", "--server.tomcat.threads.min-spare=1",
                "--server.servlet.context-path=" + contextPath, "--CATALOG_JDBC_URL=" + MYSQL.getJdbcUrl(),
                "--CATALOG_DB_USER=" + MYSQL.getUsername(), "--CATALOG_DB_PASSWORD=" + MYSQL.getPassword());
        try (org.springframework.context.ConfigurableApplicationContext active = context) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            Observation observations = context.getBean(Observation.class);
            AuthDriver driver = new AuthDriver("http://127.0.0.1:" + port + contextPath, observations);
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal("invalid"));
            driver.contextPrincipal();
            assertThrows(SecurityException.class, () -> driver.login("alice", "wrong"));
            driver.contextPrincipal();
            String writer = driver.login("writer", "demo-password");
            assertEquals(Optional.empty(), driver.contextPrincipal());
            assertEquals("writer", driver.authenticatedPrincipal(writer));
            assertEquals(Optional.empty(), driver.contextPrincipal());
            driver.call("POST", "/orders/catalog/goods/LUNCH", writer, "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n");
            assertEquals(201, driver.lastStatus);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            driver.call("POST", "/orders/catalog/orders/T001", writer, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n");
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

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>requestObservation()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @Configuration(proxyBeanMethods = false)
    static class Observation {
        /** \u6267\u884c\u6d4b\u8bd5\u5de5\u4f5c\u4efb\u52a1\u7684\u7ebf\u7a0b\u6216\u6267\u884c\u7ec4\u4ef6\u3002 */
        final java.util.Set<Long> workers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        /** \u5f53\u524d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u5c5e\u6027\u503c\u6216\u6279\u91cf\u8f93\u5165\u96c6\u5408\u3002 */
        final LinkedBlockingQueue<Optional<String>> values = new LinkedBlockingQueue<>();

        /**
         * \u8bfb\u53d6 <code>registration</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>registration</code>
         */
        @Bean
        FilterRegistrationBean<Filter> requestObservation() {
            Filter filter = (request, response, chain) -> {
                try {
                    chain.doFilter(request, response);
                } finally {
                    Object nativeContext = RequestContextHolder.getRequestAttributes();
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

    /** \u5c06 <code>base</code>\u3001<code>observation</code>\u3001<code>tenant</code>\u3001<code>client</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static class AuthDriver {
        /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
        private final String base;
        /** \u6536\u96c6\u5230\u7684\u6846\u67b6\u4e8b\u4ef6\u3001\u8c03\u7528\u6216 SQL \u89c2\u6d4b\u7ed3\u679c\u3002 */
        private final Observation observation;
        /** \u4e1a\u52a1\u6570\u636e\u6216\u8ba4\u8bc1\u8bf7\u6c42\u6240\u5c5e\u7684\u6d4b\u8bd5\u79df\u6237\u3002 */
        private final String tenant = java.util.UUID.randomUUID().toString().substring(0, 20);
        /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
        private final HttpClient client = HttpClient.newHttpClient();
        /** \u4fdd\u5b58\u5f53\u524d\u5c1a\u672a\u6267\u884c\u6216\u5c1a\u672a\u5904\u7406\u5b8c\u6210\u7684\u6d4b\u8bd5\u64cd\u4f5c\u3002 */
        private int pending;
        /** \u6700\u8fd1\u4e00\u6b21\u8bb0\u5f55\u7684\u54cd\u5e94\u6216\u6295\u9012\u72b6\u6001\u3002 */
        private int lastStatus;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>AuthDriver</code> \u5b9e\u4f8b\u3002
         * @param base \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740
         * @param observation <code>observation</code> \u5b57\u6bb5
         */
        AuthDriver(String base, Observation observation) {
            this.base = base;
            this.observation = observation;
        }

        /**
         * \u8c03\u7528 <code>call()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param username \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u7528\u6237\u540d
         * @param password \u6d4b\u8bd5\u8d26\u53f7\u7684\u53e3\u4ee4
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>String</code> \u7ed3\u679c
         */
        public String login(String username, String password) {
            return call("POST", "/auth/login?username=" + username + "&password=" + password, "", "");
        }

        /**
         * \u901a\u8fc7 <code>call()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>call("GET", "/auth/me", token, "")</code>
         */
        public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, "");
        }

        /**
         * \u8c03\u7528 <code>call()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         */
        public void logout(String token) {
            call("POST", "/auth/logout", token, "");
        }

        /**
         * \u6309 <code>(pending == 0)</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>Optional.ofNullable(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).map(Object::toString)</code>\u3001<code>Objects.requireNonNull(observation.values.poll(10, TimeUnit.SECONDS), "request observation missing")</code>
         */
        public Optional<String> contextPrincipal() {
            if (pending == 0) {
                return Optional.ofNullable(RequestContextHolder.getRequestAttributes()).map(Object::toString);
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
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>lastStatus = response.statusCode()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @return \u8be5\u5f02\u5e38\u8def\u5f84\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u7ed3\u679c
         */
        private String call(String method, String path, String token, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
                if (!token.isEmpty()) {
                    builder.header("satoken", token);
                }
                pending++;
                HttpResponse<java.lang.String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
