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

    /** \u4f7f\u7528\u9ed8\u8ba4\u56de\u9000\u6a21\u5f0f\u7684 API \u7248\u672c\u89e3\u6790\u5668\u3002 */
    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    /** \u4f7f\u7528\u4e25\u683c\u7248\u672c\u89e3\u6790\u6a21\u5f0f\u7684 API \u7248\u672c\u89e3\u6790\u5668\u3002 */
    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    /** \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684\u6846\u67b6\u4f9d\u8d56\u6ce8\u5165\u5bb9\u5668\u3002 */
    private AnnotationConfigApplicationContext applicationContext;

    /**
     * \u901a\u8fc7 <code>(exchange)-&gt;Mono.empty()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(exchange)-&gt;Mono.empty()</code>
     */
    private static WebFilterChain passThrough() {
        return exchange -> Mono.empty();
    }

    /**
     * \u6309 <code>(type.isInstance(current))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param thrown \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Throwable</code> \u53c2\u6570 <code>thrown</code>
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>current</code>\u3001<code>null</code>
     */
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

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>applicationContext.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(applicationContext)) {
            applicationContext.close();
            applicationContext = null;
        }
    }

    // ------------------------------ 过滤器单元测试 ------------------------------

    /** \u9a8c\u8bc1<code>exchange.getAttributes()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>ApiVersionWebFilter.API_VERSION_ATTRIBUTE</code>\u3001<code>ApiVersion.parse("2.1.0")</code>\u3002 */
    @Test
    void filterStoresResolvedHeaderVersionIntoExchangeAttribute() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(XHeaders.X_APP_VERSION, "2.1.0"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes())
                .containsEntry(ApiVersionWebFilter.API_VERSION_ATTRIBUTE, ApiVersion.parse("2.1.0"));
    }

    /** \u9a8c\u8bc1<code>exchange.getAttributes()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>ApiVersionWebFilter.API_VERSION_ATTRIBUTE</code>\u3001<code>ApiVersion.parse("2.0.0")</code>\u3002 */
    @Test
    void filterStoresPathVersionWhenHeaderAbsent() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v2/orders"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes())
                .containsEntry(ApiVersionWebFilter.API_VERSION_ATTRIBUTE, ApiVersion.parse("2.0.0"));
    }

    /** \u9a8c\u8bc1<code>exchange.getAttributes()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>ApiVersionWebFilter.API_VERSION_ATTRIBUTE</code>\u3001<code>ApiVersion.parse("1.0.0")</code>\u3002 */
    @Test
    void filterFallsBackToDefaultVersionWhenBothMissing() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes())
                .containsEntry(ApiVersionWebFilter.API_VERSION_ATTRIBUTE, ApiVersion.parse("1.0.0"));
    }

    /** \u9a8c\u8bc1<code>filter.isEnabled()</code>\u4e3a\u5047\u3002 */
    @Test
    void filterDisabledLeavesAttributeAbsent() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter(fallback, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v2/orders").header(XHeaders.X_APP_VERSION, "2.1.0"));

        assertThat(filter.isEnabled()).isFalse();
        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes()).doesNotContainKey(ApiVersionWebFilter.API_VERSION_ATTRIBUTE);
    }

    /** \u9a8c\u8bc1<code>thrown</code>\u975e\u7a7a\uff0c<code>rejected</code>\u975e\u7a7a\uff0c<code>rejected.getMessage()</code> \u5305\u542b <code>"2.x"</code>\u3002 */
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

    /** \u9a8c\u8bc1 WebFlux \u7248\u672c\u8fc7\u6ee4\u5668\u5728\u6784\u9020\u4e1a\u52a1\u8bf7\u6c42\u4e0a\u4e0b\u6587\u4e4b\u524d\u751f\u6548\u3002 */
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

    /** \u9a8c\u8bc1<code>result.getStatus()</code> \u7b49\u4e8e <code>HttpStatus.OK</code>\uff0c<code>result.getResponseBody()</code> \u7b49\u4e8e <code>"2.1.0"</code>\u3002 */
    @Test
    void handlerReadsApiVersionFromContext_whenHeaderPresent() {
        WebTestClient client = clientFor(WiredConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/version", "2.1.0");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).isEqualTo("2.1.0");
    }

    /** \u9a8c\u8bc1<code>result.getStatus()</code> \u7b49\u4e8e <code>HttpStatus.OK</code>\uff0c<code>result.getResponseBody()</code> \u7b49\u4e8e <code>"2.0.0"</code>\u3002 */
    @Test
    void handlerReadsApiVersionFromContext_whenPathVersionPresent() {
        WebTestClient client = clientFor(WiredConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/v2/version", null);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).isEqualTo("2.0.0");
    }

    /** \u9a8c\u8bc1<code>result.getStatus()</code> \u7b49\u4e8e <code>HttpStatus.OK</code>\uff0c<code>result.getResponseBody()</code> \u7b49\u4e8e <code>"absent"</code>\u3002 */
    @Test
    void handlerReadsNullApiVersion_whenVersionFilterNotRegistered() {
        //规格 Scenario：未接线（版本过滤器不注册）零影响，既有请求处理行为不变。
        WebTestClient client = clientFor(UnwiredConfiguration.class);

        EntityExchangeResult<String> result = getString(client, "/version", "2.1.0");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).isEqualTo("absent");
    }

    /** \u9a8c\u8bc1<code>result.getStatus()</code> \u7b49\u4e8e <code>HttpStatus.BAD_REQUEST</code>\uff0c<code>result.getResponseBody()</code> \u5305\u542b <code>"2.x"</code>\u3002 */
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

        /**
         * \u521b\u5efa <code>new ApiVersionWebFilter</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ApiVersionWebFilter(new ApiVersionResolver(ApiVersion.parse("1.0.0")))</code>
         */
        @Bean
        ApiVersionWebFilter apiVersionWebFilter() {
            return new ApiVersionWebFilter(new ApiVersionResolver(ApiVersion.parse("1.0.0")));
        }

        /**
         * \u8c03\u7528 <code>WebAccessPolicy.disabled()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jWebFluxFilter</code> \u7ed3\u679c
         */
        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        }

        /**
         * \u521b\u5efa <code>new VersionProbeController</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new VersionProbeController()</code>
         */
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

        /**
         * \u8c03\u7528 <code>WebAccessPolicy.disabled()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jWebFluxFilter</code> \u7ed3\u679c
         */
        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        }

        /**
         * \u521b\u5efa <code>new VersionProbeController</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new VersionProbeController()</code>
         */
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

        /**
         * \u521b\u5efa <code>new ApiVersionWebFilter</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ApiVersionWebFilter(new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT))</code>
         */
        @Bean
        ApiVersionWebFilter apiVersionWebFilter() {
            return new ApiVersionWebFilter(new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT));
        }

        /**
         * \u8c03\u7528 <code>WebAccessPolicy.disabled()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jWebFluxFilter</code> \u7ed3\u679c
         */
        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        }

        /**
         * \u521b\u5efa <code>new GlobalErrorWebExceptionHandler</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(), new DefaultWebExceptionTranslator())</code>
         */
        @Bean
        GlobalErrorWebExceptionHandler globalErrorWebExceptionHandler() {
            return new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),
                    new DefaultWebExceptionTranslator());
        }

        /**
         * \u521b\u5efa <code>new VersionProbeController</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new VersionProbeController()</code>
         */
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

        /**
         * \u8c03\u7528 <code>Ddd4jWebFluxContext.currentRequest()</code>\u3001<code>context.getApiVersion()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Mono&lt;String&gt;</code> \u7ed3\u679c
         */
        @GetMapping({"/version", "/v2/version"})
        public Mono<String> version() {
            return Ddd4jWebFluxContext.currentRequest()
                    .map(context -> Objects.isNull(context.getApiVersion())
                            ? "absent" : context.getApiVersion().toString())
                    .defaultIfEmpty("absent");
        }
    }
}
