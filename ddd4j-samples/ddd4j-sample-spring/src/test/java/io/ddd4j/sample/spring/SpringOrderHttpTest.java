package io.ddd4j.sample.spring;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 实际随机端口 MVC/WebFlux 验证默认订单契约及可选能力缺省边界。 */
class SpringOrderHttpTest {
    @Test
    void mvc() {
        verify(WebApplicationType.SERVLET);
    }

    @Test
    void webflux() {
        verify(WebApplicationType.REACTIVE);
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE)
    static class LocaleObservation {
        @org.springframework.context.annotation.Bean
        org.springframework.web.server.WebFilter localeObservation() {
            return (exchange, chain) -> {
                exchange.getResponse().getHeaders().set("X-Sample-Test-Locale", exchange.getLocaleContext().getLocale().toLanguageTag());
                return chain.filter(exchange);
            };
        }
    }

    private void verify(WebApplicationType type) {
        SpringApplication application = new SpringApplication(SpringSampleApplication.class);
        application.setWebApplicationType(type);
        ConfigurableApplicationContext context = application.run("--server.port=0");
        try (context) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            // 外部 Boot 保留未使用的空总线；默认业务无需执行器或可选资源。
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.cqrs.command.CommandExecutor.class).length);
            assertEquals(0, context.getBeanNamesForType(ViewManager.class).length);
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.cqrs.eventstore.EventStore.class).length);
            assertEquals(0, context.getBeanNamesForType(io.ddd4j.core.subject.SubjectProvider.class).length);
            if (type == WebApplicationType.REACTIVE) {
                org.junit.jupiter.api.Assertions.assertInstanceOf(
                        org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver.class,
                        context.getBean("localeContextResolver"));
            }
            SampleContractAssertions.defaultOrder(new HttpOrderSampleDriver("http://127.0.0.1:" + port),
                    "spring-" + type);
            if (type == WebApplicationType.REACTIVE) {
                try {
                    var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/orders/query"))
                            .header("Content-Type", "application/json").header("X-Language", "fr-CA")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"page\":1,\"size\":100}")).build();
                    var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode());
                    assertEquals("fr-CA", response.headers().firstValue("X-Sample-Test-Locale").orElseThrow());
                } catch (java.io.IOException | InterruptedException exception) {
                    throw new AssertionError(exception);
                }
            }
        }
        assertFalse(context.isActive());
    }
}
