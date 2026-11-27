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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.ddd4j.web.webflux.error.GlobalErrorAttributes;
import io.ddd4j.web.webflux.error.GlobalErrorWebExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实现 <code>AbstractWebContractTest</code>，供测试调用 <code>setUp()</code>、<code>tearDown()</code>、<code>client()</code> 并观察结果。
 */
class Ddd4jWebFluxHttpContractTest extends AbstractWebContractTest {

    /**
     * 当前用例使用的框架依赖注入容器。
     */
    private AnnotationConfigApplicationContext applicationContext;
    /**
     * 执行真实请求供公共 HTTP 合同断言使用的客户端。
     */
    private WebContractClient contractClient;

    /**
     * 测试执行前执行 <code>CacheKit.build()</code>、<code>mock()</code>、<code>BaseContext.inject()</code>、<code>provider()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        CacheKit.build("webflux-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        applicationContext = new AnnotationConfigApplicationContext(ContractConfiguration.class);
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
        contractClient = new WebFluxContractClient(webTestClient);
    }

    /**
     * 测试结束后执行 <code>applicationContext.close()</code>、<code>BaseContext.clear()</code>、<code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(applicationContext)) {
            applicationContext.close();
        }
        BaseContext.clear();
        CacheKit.unregister("webflux-contract");
    }

    /**
     * 返回发送测试请求所用的客户端。
     *
     * @return 实际返回<code>contractClient</code>，供调用方取得当前值
     */
    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    /**
     * 创建 <code>new SubjectProvider</code> 供当前测试路径调用。
     *
     * @param subject 用于构造本次返回结果的 <code>Subject</code> 输入
     * @return 当前测试使用的结果，取自 <code>new SubjectProvider(){ &#64;Override public Subject getSubject() { return subject; } }</code>
     */
    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    /**
     * 向测试容器注册 <code>contractController()</code>、<code>ddd4jWebFluxFilter()</code>、<code>globalErrorWebExceptionHandler()</code> 等测试入口或组件。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class ContractConfiguration {

        /**
         * 创建 <code>new ContractController</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new ContractController()</code>
         */
        @Bean
        ContractController contractController() {
            return new ContractController();
        }

        /**
         * 调用 <code>WebAccessPolicy.requiredExcept()</code> 执行当前夹具操作。
         *
         * @return 提供给后续契约断言的 <code>Ddd4jWebFluxFilter</code> 结果
         */
        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            BearerSubjectAuthenticator authenticator = new BearerSubjectAuthenticator();
            WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(authenticator,
                    WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(), requestLifecycle,
                    new WebIdempotencyLifecycle(new CacheIdempotencyGuard("webflux-contract")));
        }

        /**
         * 创建 <code>new GlobalErrorWebExceptionHandler</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(), new DefaultWebExceptionTranslator())</code>
         */
        @Bean
        GlobalErrorWebExceptionHandler globalErrorWebExceptionHandler() {
            return new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),
                    new DefaultWebExceptionTranslator());
        }
    }

    /**
     * 为 HTTP 合同提供实际请求路由的 Spring 测试控制器。
     */
    @RestController
    static class ContractController {

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
         */
        @GetMapping({WebContractPaths.SUCCESS, WebContractPaths.PUBLIC, WebContractPaths.PROTECTED})
        R<Map<String, String>> success() {
            return R.ok(Map.of("result", "ok"));
        }

        /**
         * 通过 <code>ResponseEntity.status()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Map.of("result", "created")))</code>
         */
        @PostMapping(WebContractPaths.CREATED)
        ResponseEntity<R<Map<String, String>>> created() {
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Map.of("result", "created")));
        }

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(context)</code>
         */
        @GetMapping(WebContractPaths.CONTEXT)
        Mono<R<Map<String, Object>>> context() {
            return Ddd4jWebFluxContext.currentRequest().map(requestContext -> {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("requestId", requestContext.requestId());
                context.put("traceId", requestContext.traceId());
                context.put("tenantId", requestContext.tenantId());
                return R.ok(context);
            });
        }

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "accepted"))</code>
         */
        @PostMapping(WebContractPaths.IDEMPOTENT)
        R<Map<String, String>> idempotent() {
            return R.ok(Map.of("result", "accepted"));
        }

        /**
         * 通过 <code>Mono.error()</code> 取得当前测试需要的结果。
         *
         * @param type 用来解析、检查或构造测试对象的 Java 类型
         * @return 当前测试使用的结果，取自 <code>Mono.error(throwable)</code>
         */
        @GetMapping("/contract/errors/{type}")
        Mono<R<Void>> error(@PathVariable("type") String type) {
            Throwable throwable = switch (type) {
                case "bad-request" -> new IllegalArgumentException("bad request");
                case "forbidden" -> new SecurityException("forbidden");
                case "not-found" -> new NoSuchElementException("not found");
                case "conflict" -> new IllegalStateException("conflict");
                case "unsupported-media-type" -> new WebStatusException(415, "unsupported media type");
                case "unprocessable-entity" -> new WebStatusException(422, "unprocessable entity");
                case "too-many-requests" -> new WebStatusException(429, "too many requests");
                default -> new RuntimeException("internal failure");
            };
            return Mono.error(throwable);
        }
    }

    /**
     * 在当前 HTTP 框架中执行共用合同的客户端适配器。
     */
    private final static class WebFluxContractClient implements WebContractClient {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 对 WebFlux 或框架真实端点发起测试请求的客户端。
         */
        private final WebTestClient webTestClient;

        /**
         * 设置 <code>webTestClient</code>，用于构造 <code>WebFluxContractClient</code> 测试实例。
         *
         * @param webTestClient 用于初始化 <code>WebFluxContractClient</code> 的 <code>WebTestClient</code> 值
         */
        @JsonCreator()
        private WebFluxContractClient(@JsonProperty("webTestClient") WebTestClient webTestClient) {
            this.webTestClient = webTestClient;
        }

        /**
         * 通过当前客户端发起 HTTP 请求。
         *
         * @param method  HTTP 方法
         * @param path    请求路径
         * @param headers 请求头
         * @param body    请求体
         * @return 响应快照
         */
        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            WebTestClient.RequestBodySpec request = webTestClient.method(HttpMethod.valueOf(method)).uri(path);
            headers.forEach(request::header);
            if (StringUtils.hasLength(body)) {
                request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            }
            EntityExchangeResult<byte[]> result = request.exchange().expectBody().returnResult();
            Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
            result.getResponseHeaders().forEach(responseHeaders::put);
            byte[] responseBody = result.getResponseBody();
            String responseText = Objects.isNull(responseBody) ? "" : new String(responseBody, StandardCharsets.UTF_8);
            return new WebContractResponse(result.getStatus().value(), responseHeaders, responseText);
        }

        /**
         * 读取<code>webTestClient</code> 字段。
         *
         * @return <code>webTestClient</code> 字段
         */
        @JsonProperty("webTestClient")
        public WebTestClient webTestClient() {
            return webTestClient;
        }

        /**
         * 根据<code>webTestClient</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            WebFluxContractClient other = (WebFluxContractClient) obj;
            return Objects.equals(this.webTestClient, other.webTestClient);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(webTestClient);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>webTestClient</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "WebFluxContractClient[webTestClient=" + webTestClient + "]";
        }
    }
}
