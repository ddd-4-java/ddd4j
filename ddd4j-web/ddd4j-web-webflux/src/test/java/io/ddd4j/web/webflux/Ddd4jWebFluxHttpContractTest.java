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

/** \u5b9e\u73b0 <code>AbstractWebContractTest</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>setUp()</code>\u3001<code>tearDown()</code>\u3001<code>client()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
class Ddd4jWebFluxHttpContractTest extends AbstractWebContractTest {

    /** \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684\u6846\u67b6\u4f9d\u8d56\u6ce8\u5165\u5bb9\u5668\u3002 */
    private AnnotationConfigApplicationContext applicationContext;
    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private WebContractClient contractClient;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>provider()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>applicationContext.close()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
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
     * \u521b\u5efa <code>new SubjectProvider</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param subject \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>Subject</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SubjectProvider(){ &#64;Override public Subject getSubject() { return subject; } }</code>
     */
    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>contractController()</code>\u3001<code>ddd4jWebFluxFilter()</code>\u3001<code>globalErrorWebExceptionHandler()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class ContractConfiguration {

        /**
         * \u521b\u5efa <code>new ContractController</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ContractController()</code>
         */
        @Bean
        ContractController contractController() {
            return new ContractController();
        }

        /**
         * \u8c03\u7528 <code>WebAccessPolicy.requiredExcept()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jWebFluxFilter</code> \u7ed3\u679c
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
         * \u521b\u5efa <code>new GlobalErrorWebExceptionHandler</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(), new DefaultWebExceptionTranslator())</code>
         */
        @Bean
        GlobalErrorWebExceptionHandler globalErrorWebExceptionHandler() {
            return new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),
                    new DefaultWebExceptionTranslator());
        }
    }

    /** \u4e3a HTTP \u5408\u540c\u63d0\u4f9b\u5b9e\u9645\u8bf7\u6c42\u8def\u7531\u7684 Spring \u6d4b\u8bd5\u63a7\u5236\u5668\u3002 */
    @RestController
    static class ContractController {

        /**
         * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
         */
        @GetMapping({WebContractPaths.SUCCESS, WebContractPaths.PUBLIC, WebContractPaths.PROTECTED})
        R<Map<String, String>> success() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        /**
         * \u901a\u8fc7 <code>ResponseEntity.status()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Map.of("result", "created")))</code>
         */
        @PostMapping(WebContractPaths.CREATED)
        ResponseEntity<R<Map<String, String>>> created() {
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Collections.singletonMap("result", "created")));
        }

        /**
         * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(context)</code>
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
         * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "accepted"))</code>
         */
        @PostMapping(WebContractPaths.IDEMPOTENT)
        R<Map<String, String>> idempotent() {
            return R.ok(Collections.singletonMap("result", "accepted"));
        }

        /**
         * \u901a\u8fc7 <code>Mono.error()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Mono.error(throwable)</code>
         */
        @GetMapping("/contract/errors/{type}")
        Mono<R<Void>> error(@PathVariable("type") String type) {
            Throwable throwable;
            switch (type) {
                case "bad-request":
                    throwable = new IllegalArgumentException("bad request");
                    break;
                case "forbidden":
                    throwable = new SecurityException("forbidden");
                    break;
                case "not-found":
                    throwable = new NoSuchElementException("not found");
                    break;
                case "conflict":
                    throwable = new IllegalStateException("conflict");
                    break;
                case "unsupported-media-type":
                    throwable = new WebStatusException(415, "unsupported media type");
                    break;
                case "unprocessable-entity":
                    throwable = new WebStatusException(422, "unprocessable entity");
                    break;
                case "too-many-requests":
                    throwable = new WebStatusException(429, "too many requests");
                    break;
                default:
                    throwable = new RuntimeException("internal failure");
                    break;
            }
            return Mono.error(throwable);
        }
    }

    /** \u5728\u5f53\u524d HTTP \u6846\u67b6\u4e2d\u6267\u884c\u5171\u7528\u5408\u540c\u7684\u5ba2\u6237\u7aef\u9002\u914d\u5668\u3002 */
    private static final class WebFluxContractClient implements WebContractClient {
        /** \u5bf9 WebFlux \u6216\u6846\u67b6\u771f\u5b9e\u7aef\u70b9\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
        private final WebTestClient webTestClient;

        /**
         * \u8bbe\u7f6e <code>webTestClient</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>WebFluxContractClient</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param webTestClient \u7528\u4e8e\u521d\u59cb\u5316 <code>WebFluxContractClient</code> \u7684 <code>WebTestClient</code> \u503c
         */
        public WebFluxContractClient(WebTestClient webTestClient) {
            this.webTestClient = webTestClient;
        }

        /**
         * \u8bfb\u53d6<code>webTestClient</code> \u5b57\u6bb5\u3002
         * @return <code>webTestClient</code> \u5b57\u6bb5
         */
        public WebTestClient webTestClient() {
            return webTestClient;
        }

        /**
         * 按 <code>equals</code> 的契约计算并返回结果。
         *
         * @param o 传入此操作的 <code>Object</code> 类型 <code>o</code>
         * @return 按条件返回 <code>true</code>、<code>false</code>、<code>Objects.equals(this.webTestClient, other.webTestClient)</code>
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WebFluxContractClient)) return false;
            WebFluxContractClient other = (WebFluxContractClient) o;
            return Objects.equals(this.webTestClient, other.webTestClient);
        }

        /**
         * 用<code>webTestClient</code>计算哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return java.util.Objects.hash(webTestClient);
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>webTestClient</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "WebFluxContractClient{" + "webTestClient=" + webTestClient + "}";
        }

        /**
         * 通过当前客户端发起 HTTP 请求。
         *
         * @param method HTTP 方法
         * @param path 请求路径
         * @param headers 请求头
         * @param body 请求体
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
            String responseText = Objects.isNull(responseBody)
                    ? "" : new String(responseBody, StandardCharsets.UTF_8);
            return new WebContractResponse(result.getStatus().value(), responseHeaders, responseText);
        }

    }
}
