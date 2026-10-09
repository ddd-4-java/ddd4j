/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webflux;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.version.ApiVersionMode;
import io.ddd4j.web.core.version.ApiVersionRejectedException;
import io.ddd4j.web.core.version.ApiVersionResolver;
import io.ddd4j.web.webflux.error.GlobalErrorAttributes;
import io.ddd4j.web.webflux.error.GlobalErrorWebExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * {@link ApiVersionWebFilter} 单元测试与 WebFlux 运行时接线集成测试。
 *
 * <p>对应规格 Scenario（版本上下文注入）：WebFlux 注入业务处理器可读 API 版本、未接线零影响；
 * 以及 API 版本解析的非法值拒绝在响应式链路上的 4xx 表现。
 */
class ApiVersionWebFilterTest {

    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    private AnnotationConfigApplicationContext applicationContext;

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(applicationContext)) {
            applicationContext.close();
            applicationContext = null;
        }
    }

    private static WebFilterChain passThrough() {
        return exchange -> Mono.empty();
    }

    private static Throwable findCause(Throwable thrown, Class<? extends Throwable> type) {
        Throwable current = thrown;
        while (Objects.nonNull(current)) {
            if (type.isInstance(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    // ------------------------------ 过滤器单元测试 ------------------------------

    @Test
    void filterStoresResolvedHeaderVersionIntoExchangeAttribute() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(XHeaders.X_APP_VERSION, "2.1.0"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes())
                .containsEntry(ApiVersionWebFilter.API_VERSION_ATTRIBUTE, ApiVersion.parse("2.1.0"));
    }

    @Test
    void filterStoresPathVersionWhenHeaderAbsent() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v2/orders"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes())
                .containsEntry(ApiVersionWebFilter.API_VERSION_ATTRIBUTE, ApiVersion.parse("2.0.0"));
    }

    @Test
    void filterFallsBackToDefaultVersionWhenBothMissing() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes())
                .containsEntry(ApiVersionWebFilter.API_VERSION_ATTRIBUTE, ApiVersion.parse("1.0.0"));
    }

    @Test
    void filterDisabledLeavesAttributeAbsent() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v2/orders").header(XHeaders.X_APP_VERSION, "2.1.0"));

        assertThat(filter.isEnabled()).isFalse();
        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes()).doesNotContainKey(ApiVersionWebFilter.API_VERSION_ATTRIBUTE);
    }

    @Test
    void filterStrictModeRejectsIllegalVersionWith400Signal() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(strict);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(XHeaders.X_APP_VERSION, "2.x"));

        Throwable thrown = catchThrowable(() -> filter.filter(exchange, passThrough()).block());

        assertThat(thrown).isNotNull();
        Throwable rejected = findCause(thrown, ApiVersionRejectedException.class);
        assertThat(rejected).isNotNull();
        assertThat(((ApiVersionRejectedException) rejected).getStatus()).isEqualTo(400);
        assertThat(rejected.getMessage()).contains("2.x");
        assertThat(exchange.getAttributes()).doesNotContainKey(ApiVersionWebFilter.API_VERSION_ATTRIBUTE);
    }

    @Test
    void filterOrderPrecedesContextFilter() {
        assertThat(new ApiVersionWebFilter(fallback).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    // ------------------------------ WebTestClient 集成测试 ------------------------------

    /**
     * 以给定配置启动独立应用上下文并绑定测试客户端。
     *
     * @param configuration 测试用 Spring 配置类
     * @return 绑定该上下文的测试客户端
     */
    private WebTestClient clientFor(Class<?> configuration) {
        if (Objects.nonNull(applicationContext)) {
            applicationContext.close();
        }
        applicationContext = new AnnotationConfigApplicationContext(configuration);
        return WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    /**
     * 以 GET 发起请求并返回状态码与响应体（沿用仓库既有风格，不使用 matcher 断言）。
     *
     * @param client        绑定了测试上下文的客户端
     * @param uri           请求路径
     * @param headerVersion {@code X-App-Version} 头值，为 {@code null} 时不携带该头
     * @return 响应实体结果，含状态码与响应体
     */
    private EntityExchangeResult<String> getString(WebTestClient client, String uri, String headerVersion) {
        WebTestClient.RequestBodySpec request = client.method(HttpMethod.GET).uri(uri);
        if (Objects.nonNull(headerVersion)) {
            request.header(XHeaders.X_APP_VERSION, headerVersion);
        }
        return request.exchange().expectBody(String.class).returnResult();
    }

    @Test
    void handlerReadsApiVersionFromContext_whenHeaderPresent() {
        WebTestClient client = clientFor(WiredConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/version", "2.1.0");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).isEqualTo("2.1.0");
    }

    @Test
    void handlerReadsApiVersionFromContext_whenPathVersionPresent() {
        WebTestClient client = clientFor(WiredConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/v2/version", null);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).isEqualTo("2.0.0");
    }

    @Test
    void handlerReadsNullApiVersion_whenVersionFilterNotRegistered() {
        //规格 Scenario：未接线（版本过滤器不注册）零影响，既有请求处理行为不变。
        WebTestClient client = clientFor(UnwiredConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/version", "2.1.0");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).isEqualTo("absent");
    }

    @Test
    void strictModeRejectsIllegalVersionWith400Response() {
        WebTestClient client = clientFor(StrictConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/version", "2.x");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponseBody()).contains("2.x");
    }

    /**
     * 已接线配置：版本过滤器 + 上下文过滤器 + 版本探针控制器。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class WiredConfiguration {

        @Bean
        ApiVersionWebFilter apiVersionWebFilter() {
            return new ApiVersionWebFilter(new ApiVersionResolver(ApiVersion.parse("1.0.0")));
        }

        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        }

        @Bean
        VersionProbeController versionProbeController() {
            return new VersionProbeController();
        }
    }

    /**
     * 未接线配置：不注册版本过滤器，仅上下文过滤器（验证零影响）。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class UnwiredConfiguration {

        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        }

        @Bean
        VersionProbeController versionProbeController() {
            return new VersionProbeController();
        }
    }

    /**
     * 严格模式配置：版本过滤器 + 全局异常处理（验证非法版本值 4xx）。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class StrictConfiguration {

        @Bean
        ApiVersionWebFilter apiVersionWebFilter() {
            return new ApiVersionWebFilter(new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT));
        }

        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        }

        @Bean
        GlobalErrorWebExceptionHandler globalErrorWebExceptionHandler() {
            return new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),
                    new DefaultWebExceptionTranslator());
        }

        @Bean
        VersionProbeController versionProbeController() {
            return new VersionProbeController();
        }
    }

    /**
     * 版本探针控制器：从 Reactor Context 中的 {@link io.ddd4j.web.core.context.WebRequestContext}
     * 回显解析出的 API 版本（未接线时回显 {@code absent}）。
     */
    @RestController
    static class VersionProbeController {

        @GetMapping({"/version", "/v2/version"})
        public Mono<String> version() {
            return Ddd4jWebFluxContext.currentRequest()
                    .map(context -> Objects.isNull(context.getApiVersion())
                            ? "absent" : context.getApiVersion().toString())
                    .defaultIfEmpty("absent");
        }
    }
}
