package io.ddd4j.sample.spring.satoken;

import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.RequestContextHolder;
import jakarta.servlet.Filter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 原生引擎通过真实 Servlet/HTTP/DI 执行鉴权；外层测试过滤器只观察，不清理上下文。 */
class SpringSatokenHttpTest {
    @Test
    void nativeAuthAndRequestCleanup() {
        verify("");
    }

    @Test
    void nativeAuthWithContextPathAndRequestCleanup() {
        verify("/sample");
    }

    private void verify(String contextPath) {
        var context = SpringApplication.run(SpringSatokenSampleApplication.class, "--server.port=0", "--server.tomcat.threads.max=1", "--server.tomcat.threads.min-spare=1",
                "--server.servlet.context-path=" + contextPath);
        try (context) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var observations = context.getBean(Observation.class);
            AuthDriver driver = new AuthDriver("http://127.0.0.1:" + port + contextPath, observations);
            assertThrows(SecurityException.class, () -> driver.authenticatedPrincipal("invalid"));
            driver.contextPrincipal();
            assertThrows(SecurityException.class, () -> driver.login("alice", "wrong"));
            driver.contextPrincipal();
            String reader = driver.login("alice", "demo-password");
            assertEquals(Optional.empty(), driver.contextPrincipal());
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> assertForbidden(driver, "POST", "/orders", reader,
                            "{\"orderNo\":\"readonly-denied\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}"),
                    () -> assertForbidden(driver, "DELETE", "/orders/missing", reader, ""));
            driver.logout(reader);
            assertEquals(Optional.empty(), driver.contextPrincipal());
            SampleContractAssertions.authentication("satoken", driver, "alice", "demo-password",
                    "alice", "order:read", "order:cancel", "[]");
            verifyAuthorizedWrite(driver, context);
            org.junit.jupiter.api.Assertions.assertEquals(1, observations.workers.size(), "same actual worker reused");
        }
        assertFalse(context.isActive());
    }

    private static void assertForbidden(AuthDriver driver, String method, String path, String token, String body) {
        try {
            assertThrows(SecurityException.class, () -> driver.call(method, path, token, body));
            assertEquals(403, driver.lastStatus);
        } finally {
            assertEquals(Optional.empty(), driver.contextPrincipal(), "denied request server cleanup");
        }
    }

    private static void createAndRead(AuthDriver driver) {
        String token = driver.login("alice", "demo-password");
        assertEquals(Optional.empty(), driver.contextPrincipal());
        String id = driver.call("POST", "/orders", token,
                "{\"orderNo\":\"authorized-write\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
        assertEquals(200, driver.lastStatus);
        assertEquals(Optional.empty(), driver.contextPrincipal());
        String row = driver.call("GET", "/orders/" + id, token, "");
        assertEquals(200, driver.lastStatus);
        try {
            assertEquals(id, new com.fasterxml.jackson.databind.ObjectMapper().readTree(row).get("id").asText());
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        assertEquals(Optional.empty(), driver.contextPrincipal());
        driver.logout(token);
        assertEquals(Optional.empty(), driver.contextPrincipal());
    }

    private static void verifyAuthorizedWrite(AuthDriver driver, org.springframework.context.ConfigurableApplicationContext context) {
        // 仅测试临时提供原生权限数据；真实登录、token 和权限校验链保持不变。
        var original = cn.dev33.satoken.SaManager.getStpInterface();
        cn.dev33.satoken.SaManager.setStpInterface(new cn.dev33.satoken.stp.StpInterface() {
            @Override public java.util.List<String> getPermissionList(Object loginId, String loginType) {
                return "alice".equals(loginId) ? java.util.List.of("order:read", "order:write") : java.util.List.of();
            }
            @Override public java.util.List<String> getRoleList(Object loginId, String loginType) {
                return original.getRoleList(loginId, loginType);
            }
        });
        try {
            createAndRead(driver);
        } finally {
            cn.dev33.satoken.SaManager.setStpInterface(original);
        }
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

    private static class AuthDriver implements AuthSampleDriver {
        private final String base;
        private final Observation observation;
        private final HttpClient client = HttpClient.newHttpClient();
        private int pending;
        private int lastStatus;
        AuthDriver(String base, Observation observation) {
            this.base = base;
            this.observation = observation;
        }
        @Override public String login(String username, String password) {
            return call("POST", "/auth/login?username=" + username + "&password=" + password, "", "");
        }
        @Override public String authenticatedPrincipal(String token) {
            return call("GET", "/auth/me", token, "");
        }
        @Override public String authorizedResource(String token, String permission) {
            return "order:read".equals(permission)
                    ? call("POST", "/orders/query", token, "{\"page\":1,\"size\":100}")
                    : call("DELETE", "/orders/missing", token, "");
        }
        @Override public void logout(String token) {
            call("POST", "/auth/logout", token, "");
        }
        @Override public void failingRequest(String token) {
            call("GET", "/orders/missing", token, "");
        }
        @Override public Optional<String> contextPrincipal() {
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
        private String call(String method, String path, String token, String body) {
            try {
                var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
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
