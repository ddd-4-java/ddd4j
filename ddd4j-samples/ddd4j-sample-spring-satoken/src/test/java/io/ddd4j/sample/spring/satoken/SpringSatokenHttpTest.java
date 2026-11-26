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
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private static void assertForbidden(AuthDriver driver, String method, String path, String token, String body) {
        try {
            assertThrows(SecurityException.class, () -> driver.call(method, path, token, body));
            assertEquals(403, driver.lastStatus);
        } finally {
            assertEquals(Optional.empty(), driver.contextPrincipal(), "denied request server cleanup");
        }
    }

    @Test
    void nativeAuthAndRequestCleanup() {
        verify("");
    }

    @Test
    void nativeAuthWithContextPathAndRequestCleanup() {
        verify("/sample");
    }

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

    @Configuration(proxyBeanMethods = false)
    static class Observation {
        final java.util.Set<Long> workers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final LinkedBlockingQueue<Optional<String>> values = new LinkedBlockingQueue<>();

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

    private static class AuthDriver {
        private final String base;
        private final Observation observation;
        private final String tenant = java.util.UUID.randomUUID().toString().substring(0, 20);
        private final HttpClient client = HttpClient.newHttpClient();
        private int pending;
        private int lastStatus;

        AuthDriver(String base, Observation observation) {
            this.base = base;
            this.observation = observation;
        }

        public String login(String username, String password) {
            return call("POST", "/auth/login?username=" + username + "&password=" + password, "", "");
        }

        public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, "");
        }

        public void logout(String token) {
            call("POST", "/auth/logout", token, "");
        }

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

        private String call(String method, String path, String token, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("X-Tenant-Id", tenant)
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
