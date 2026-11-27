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
package io.ddd4j.web.webmvc;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebContextScope;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
class Ddd4jWebMvcContractTest extends AbstractWebContractTest {

    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private WebContractClient contractClient;

    /**
     * 基于 MockMvc 的契约客户端，将契约请求转发给独立装配的 MockMvc 执行。
     *
     * @param mockMvc 模拟 MVC 实例
     * @author ddd4j
     */
    private final static class MockMvcContractClient implements WebContractClient {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u6267\u884c Spring MVC \u5408\u540c\u8bf7\u6c42\u7684\u6d4b\u8bd5\u5ba2\u6237\u7aef\u3002 */
        private final MockMvc mockMvc;

        /**
         * request() 方法。
         * @param method 被调用的方法。
         * @param path 路径。
         * @param headers 消息头。
         * @param body 字符串参数。
         * @return 方法执行结果。
         */
        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            try {
                MockHttpServletResponse response = mockMvc.perform(requestBuilder(method, path, headers, body)).andReturn().getResponse();
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                response.getHeaderNames().forEach(name -> responseHeaders.put(name, response.getHeaders(name)));
                return new WebContractResponse(response.getStatus(), responseHeaders, response.getContentAsString());
            } catch (Exception exception) {
                throw new IllegalStateException("WebMVC contract request failed", exception);
            }
        }

        /**
         * \u8bfb\u53d6 <code>builder</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         * @param headers \u5f53\u524d\u6d4b\u8bd5\u8bf7\u6c42\u643a\u5e26\u7684\u5934\u5b57\u6bb5
         * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>builder</code>
         */
        private MockHttpServletRequestBuilder requestBuilder(String method, String path, Map<String, String> headers, String body) {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
            builder.accept(MediaType.APPLICATION_JSON);
            headers.forEach(builder::header);
            if (StringUtils.hasLength(body)) {
                builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return builder;
        }

        /**
         * \u8bbe\u7f6e <code>mockMvc</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>MockMvcContractClient</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param mockMvc \u7528\u4e8e\u521d\u59cb\u5316 <code>MockMvcContractClient</code> \u7684 <code>MockMvc</code> \u503c
         */
        @JsonCreator()
        private MockMvcContractClient(@JsonProperty("mockMvc") MockMvc mockMvc) {
            this.mockMvc = mockMvc;
        }

        /**
         * \u8bfb\u53d6<code>mockMvc</code> \u5b57\u6bb5\u3002
         * @return <code>mockMvc</code> \u5b57\u6bb5
         */
        @JsonProperty("mockMvc")
        public MockMvc mockMvc() {
            return mockMvc;
        }

        /**
         * 根据<code>mockMvc</code>比较对象的值是否相等。
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
            MockMvcContractClient other = (MockMvcContractClient) obj;
            return Objects.equals(this.mockMvc, other.mockMvc);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(mockMvc);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>mockMvc</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "MockMvcContractClient[mockMvc=" + mockMvc + "]";
        }
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>provider()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        CacheKit.build("webmvc-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        BearerSubjectAuthenticator authenticator = new BearerSubjectAuthenticator();
        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
        Ddd4jWebMvcInterceptor interceptor = new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                requestLifecycle, new WebIdempotencyLifecycle(new CacheIdempotencyGuard("webmvc-contract")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
                .build();
        contractClient = new MockMvcContractClient(mockMvc);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        /**
         * BaseContext.clear() 方法。
         */
        BaseContext.clear();
        CacheKit.unregister("webmvc-contract");
    }

    /**
     * client() 方法。
     * @return 方法执行结果。
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
            /**
             * 获取值。
             * @return 方法执行结果。
             */
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    /**
     * 契约测试用控制器，提供成功、创建、上下文、幂等与各类错误场景端点。
     *
     * @author ddd4j
     */
    @RestController
    static class ContractController {

        /**
         * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
         */
        @GetMapping({WebContractPaths.SUCCESS, WebContractPaths.PUBLIC, WebContractPaths.PROTECTED})
        R<Map<String, String>> success() {
            return R.ok(Map.of("result", "ok"));
        }

        /**
         * \u901a\u8fc7 <code>ResponseEntity.status()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Map.of("result", "created")))</code>
         */
        @PostMapping(WebContractPaths.CREATED)
        ResponseEntity<R<Map<String, String>>> created() {
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Map.of("result", "created")));
        }

        /**
         * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(context)</code>
         */
        @GetMapping(WebContractPaths.CONTEXT)
        R<Map<String, Object>> context() {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            context.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            context.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            return R.ok(context);
        }

        /**
         * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "accepted"))</code>
         */
        @PostMapping(WebContractPaths.IDEMPOTENT)
        R<Map<String, String>> idempotent() {
            return R.ok(Map.of("result", "accepted"));
        }

        /**
         * \u6784\u9020 <code>IllegalArgumentException</code>\u3001<code>SecurityException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>R&lt;Void&gt;</code> \u7ed3\u679c
         */
        @GetMapping("/contract/errors/{type}")
        R<Void> error(@PathVariable("type") String type) {
            throw switch (type) {
                case "bad-request" -> new IllegalArgumentException("bad request");
                case "forbidden" -> new SecurityException("forbidden");
                case "not-found" -> new NoSuchElementException("not found");
                case "conflict" -> new IllegalStateException("conflict");
                case "unsupported-media-type" -> new WebStatusException(415, "unsupported media type");
                case "unprocessable-entity" -> new WebStatusException(422, "unprocessable entity");
                case "too-many-requests" -> new WebStatusException(429, "too many requests");
                default -> new RuntimeException("internal failure");
            };
        }
    }
}
