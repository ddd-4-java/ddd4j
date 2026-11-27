package io.ddd4j.sample.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.NoSuchElementException;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 实际随机端口 MVC/WebFlux 验证默认订单契约及可选能力缺省边界。
 */
@Testcontainers
class SpringOrderHttpTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u8c03\u7528 <code>application.setWebApplicationType()</code>\u3001<code>application.run()</code>\u3001<code>MYSQL.getJdbcUrl()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ConfigurableApplicationContext</code> \u7ed3\u679c
     */
    private static ConfigurableApplicationContext start(WebApplicationType type) {
        SpringApplication application = new SpringApplication(SpringSampleApplication.class);
        application.setWebApplicationType(type);
        String profile = type == WebApplicationType.REACTIVE ? "catalog-trade,catalog-reactive" : "catalog-trade";
        return application.run("--spring.profiles.active=" + profile, "--server.port=0",
                "--CATALOG_JDBC_URL=" + MYSQL.getJdbcUrl(), "--CATALOG_DB_USER=" + MYSQL.getUsername(),
                "--CATALOG_DB_PASSWORD=" + MYSQL.getPassword());
    }

    /**
     * \u8bfb\u53d6 <code>response</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
     * @param tenant \u5f53\u524d\u8bf7\u6c42\u7684\u6570\u636e\u79df\u6237
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response</code>
     */
    private static HttpResponse<String> request(ConfigurableApplicationContext context, String tenant, String method,
                                                String path, int status, String body) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "").header("X-Language", "fr-CA")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<java.lang.String> response = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode(), response.body());
            return response;
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    /** \u5728 Servlet \u5e94\u7528\u4e0b\u9a8c\u8bc1\u5546\u54c1\u4ea4\u6613 HTTP \u5408\u540c\u3002 */
    @Test
    void mvc() {
        verify(WebApplicationType.SERVLET);
    }

    /** \u5728\u54cd\u5e94\u5f0f\u5e94\u7528\u4e0b\u9a8c\u8bc1\u5546\u54c1\u4ea4\u6613 HTTP \u5408\u540c\u3002 */
    @Test
    void webflux() {
        verify(WebApplicationType.REACTIVE);
    }

    /**
     * \u8c03\u7528 <code>UUID.randomUUID()</code>\u3001<code>start()</code>\u3001<code>context.getBeanNamesForType()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     */
    private void verify(WebApplicationType type) {
        String tenantId = UUID.randomUUID().toString().substring(0, 20);
        try (ConfigurableApplicationContext context = start(type)) {
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.cqrs.command.CommandExecutor.class).length);
            assertEquals(0, context.getBeanNamesForType(ViewManager.class).length);
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.cqrs.eventstore.EventStore.class).length);
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.subject.SubjectProvider.class).length);
            if (type == WebApplicationType.REACTIVE) {
                org.junit.jupiter.api.Assertions.assertInstanceOf(
                        org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver.class,
                        context.getBean("localeContextResolver"));
            }
            request(context, tenantId, "POST", "/api/goods/LUNCH", 201, "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n");
            request(context, tenantId, "POST", "/api/orders/T001", 201, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n");
            request(context, tenantId, "POST", "/api/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            request(context, tenantId, "POST", "/orders", 404, "{}");
            if (type == WebApplicationType.REACTIVE) {
                assertEquals("fr-CA", request(context, tenantId, "GET", "/api/orders/T001", 200, "")
                        .headers().firstValue("X-Sample-Test-Locale").orElseThrow(NoSuchElementException::new));
            }
        }
        try (ConfigurableApplicationContext reopened = start(type)) {
            HttpResponse<java.lang.String> result = request(reopened, tenantId, "GET", "/api/orders/T001", 200, "");
            org.junit.jupiter.api.Assertions.assertTrue(result.body().contains("\"paidAmountCent\":2500"), result.body());
        }
    }

    /** \u63d0\u4f9b <code>localeObservation()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE)
    static class LocaleObservation {
        /**
         * \u901a\u8fc7 <code>chain.filter()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>chain.filter(exchange)</code>
         */
        @org.springframework.context.annotation.Bean
        org.springframework.web.server.WebFilter localeObservation() {
            return (exchange, chain) -> {
                exchange.getResponse().getHeaders().set("X-Sample-Test-Locale", exchange.getLocaleContext().getLocale().toLanguageTag());
                return chain.filter(exchange);
            };
        }
    }
}
