package io.ddd4j.sample.spring;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 实际随机端口 MVC/WebFlux 验证默认订单契约及可选能力缺省边界。
 */
@Testcontainers
class SpringOrderHttpTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 调用 <code>application.setWebApplicationType()</code>、<code>application.run()</code>、<code>MYSQL.getJdbcUrl()</code> 执行当前夹具操作。
     *
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     * @return 提供给后续契约断言的 <code>ConfigurableApplicationContext</code> 结果
     */
    private static ConfigurableApplicationContext start(WebApplicationType type) {
        SpringApplication application = new SpringApplication(SpringSampleApplication.class);
        application.setWebApplicationType(type);
        return application.run("--spring.profiles.active=catalog-trade", "--server.port=0",
                "--CATALOG_JDBC_URL=" + MYSQL.getJdbcUrl(), "--CATALOG_DB_USER=" + MYSQL.getUsername(),
                "--CATALOG_DB_PASSWORD=" + MYSQL.getPassword());
    }

    /**
     * 读取 <code>response</code> 供当前测试使用。
     *
     * @param context 当前框架保存请求或运行时状态的上下文
     * @param tenant  当前请求的数据租户
     * @param method  发给被测服务的 HTTP 方法
     * @param path    测试请求的资源路径或待访问文件路径
     * @param status  当前 HTTP 或业务流程的状态
     * @param body    当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response</code>
     */
    private static HttpResponse<String> request(ConfigurableApplicationContext context, String tenant, String method,
                                                String path, int status, String body) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").header("X-Tenant-Id", tenant).header("X-Language", "fr-CA")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var response = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode(), response.body());
            return response;
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    /**
     * 在 Servlet 应用下验证商品交易 HTTP 合同。
     */
    @Test
    void mvc() {
        verify(WebApplicationType.SERVLET);
    }

    /**
     * 在响应式应用下验证商品交易 HTTP 合同。
     */
    @Test
    void webflux() {
        verify(WebApplicationType.REACTIVE);
    }

    /**
     * 调用 <code>UUID.randomUUID()</code>、<code>start()</code>、<code>context.getBeanNamesForType()</code> 执行当前夹具操作。
     *
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     */
    private void verify(WebApplicationType type) {
        String tenantId = UUID.randomUUID().toString().substring(0, 20);
        try (var context = start(type)) {
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.cqrs.command.CommandExecutor.class).length);
            assertEquals(0, context.getBeanNamesForType(ViewManager.class).length);
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.cqrs.eventstore.EventStore.class).length);
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.subject.SubjectProvider.class).length);
            if (type == WebApplicationType.REACTIVE) {
                org.junit.jupiter.api.Assertions.assertInstanceOf(
                        org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver.class,
                        context.getBean("localeContextResolver"));
            }
            request(context, tenantId, "POST", "/api/goods/LUNCH", 201, """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """);
            request(context, tenantId, "POST", "/api/orders/T001", 201, """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """);
            request(context, tenantId, "POST", "/api/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            request(context, tenantId, "POST", "/orders", 404, "{}");
            if (type == WebApplicationType.REACTIVE) {
                assertEquals("fr-CA", request(context, tenantId, "GET", "/api/orders/T001", 200, "")
                        .headers().firstValue("X-Sample-Test-Locale").orElseThrow());
            }
        }
        try (var reopened = start(type)) {
            var result = request(reopened, tenantId, "GET", "/api/orders/T001", 200, "");
            org.junit.jupiter.api.Assertions.assertTrue(result.body().contains("\"paidAmountCent\":2500"), result.body());
        }
    }

    /**
     * 提供 <code>localeObservation()</code> 等供当前测试使用的独立操作。
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE)
    static class LocaleObservation {
        /**
         * 通过 <code>chain.filter()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>chain.filter(exchange)</code>
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
