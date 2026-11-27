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
package io.ddd4j.web.javalin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 <code>Ddd4jJavalinWeb</code> 在<code>readinessEndpointReflectsRegistry</code>等操作中的行为契约。
 */
class Ddd4jJavalinWebContractTest extends AbstractWebContractTest {

    /**
     * 由当前测试启动和关闭的应用实例。
     */
    private Javalin app;
    /**
     * 执行真实请求供公共 HTTP 合同断言使用的客户端。
     */
    private WebContractClient contractClient;
    /**
     * 汇总当前组件就绪状态的注册表。
     */
    private RuntimeReadinessRegistry readinessRegistry;

    /**
     * 测试执行前执行 <code>CacheKit.build()</code>、<code>mock()</code>、<code>BaseContext.inject()</code>、<code>provider()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        CacheKit.build("javalin-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
        readinessRegistry = new RuntimeReadinessRegistry();
        Ddd4jJavalinWeb ddd4jWeb = new Ddd4jJavalinWeb(new WebRequestContextFactory(), requestLifecycle,
                new DefaultWebExceptionTranslator(),
                new WebIdempotencyLifecycle(new CacheIdempotencyGuard("javalin-contract")), readinessRegistry);
        app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson());
            ddd4jWeb.configure(config);
            registerContractRoutes(config.routes);
        }).start(0);
        contractClient = new JavalinContractClient(HttpClient.newHttpClient(), app.port());
    }

    /**
     * 测试结束后执行 <code>app.stop()</code>、<code>ThreadContext.clear()</code>、<code>BaseContext.clear()</code>、<code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("javalin-contract");
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
     * 验证<code>ready.status()</code> 等于 <code>200</code>，<code>ready.body()</code> 包含 <code>"\"ready\":true"</code>，<code>unavailable.body()</code> 包含 <code>"\"ready\":false"</code>。
     */
    @Test
    void readinessEndpointReflectsRegistryWithoutLeakingDetails() {
        WebContractResponse ready = client().request("GET", ReadinessEndpoint.PATH, Collections.emptyMap(), null);
        assertThat(ready.status()).isEqualTo(200);
        assertThat(ready.body()).contains("\"ready\":true");

        readinessRegistry.register(() -> ReadinessResult.unavailable("database", "secret diagnostic"));
        WebContractResponse unavailable = client().request("GET", ReadinessEndpoint.PATH, Collections.emptyMap(), null);
        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.body()).contains("\"ready\":false")
                .doesNotContain("database", "secret diagnostic");
    }

    /**
     * 调用 <code>routes.get()</code>、<code>context.json()</code>、<code>R.ok()</code> 执行当前夹具操作。
     *
     * @param routes 传给 <code>routes.get()</code> 的 <code>io.javalin.config.RoutesConfig</code> 输入
     */
    private void registerContractRoutes(io.javalin.config.RoutesConfig routes) {
        routes.get(WebContractPaths.SUCCESS, context -> context.json(R.ok(Map.of("result", "ok"))));
        routes.get(WebContractPaths.PUBLIC, context -> context.json(R.ok(Map.of("result", "ok"))));
        routes.get(WebContractPaths.PROTECTED, context -> context.json(R.ok(Map.of("result", "ok"))));
        routes.post(WebContractPaths.CREATED, context -> context.status(201)
                .json(R.ok(Map.of("result", "created"))));
        routes.get(WebContractPaths.CONTEXT, context -> {
            Map<String, Object> requestContext = new LinkedHashMap<>();
            requestContext.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            requestContext.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            requestContext.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            context.json(R.ok(requestContext));
        });
        routes.post(WebContractPaths.IDEMPOTENT,
                context -> context.json(R.ok(Map.of("result", "accepted"))));
        routes.get("/contract/errors/{type}", context -> {
            String type = context.pathParam("type");
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
        });
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
     * 在当前 HTTP 框架中执行共用合同的客户端适配器。
     */
    private final static class JavalinContractClient implements WebContractClient {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 用于发送 HTTP 请求的客户端。
         */
        private final HttpClient httpClient;

        /**
         * 当前夹具服务绑定的本地端口。
         */
        private final int port;

        /**
         * 用给定值初始化 <code>JavalinContractClient</code> 实例。
         *
         * @param httpClient 用于发送 HTTP 请求的客户端
         * @param port       <code>port</code> 字段
         */
        @JsonCreator()
        private JavalinContractClient(@JsonProperty("httpClient") HttpClient httpClient, @JsonProperty("port") int port) {
            this.httpClient = httpClient;
            this.port = port;
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
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path));
                headers.forEach(builder::header);
                HttpRequest.BodyPublisher publisher = Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
                HttpResponse<String> response = httpClient.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
                return new WebContractResponse(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Javalin contract request interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("Javalin contract request failed", exception);
            }
        }

        /**
         * 读取用于发送 HTTP 请求的客户端。
         *
         * @return 用于发送 HTTP 请求的客户端
         */
        @JsonProperty("httpClient")
        public HttpClient httpClient() {
            return httpClient;
        }

        /**
         * 读取<code>port</code> 字段。
         *
         * @return <code>port</code> 字段
         */
        @JsonProperty("port")
        public int port() {
            return port;
        }

        /**
         * 根据<code>httpClient</code>、<code>port</code>比较对象的值是否相等。
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
            JavalinContractClient other = (JavalinContractClient) obj;
            return Objects.equals(this.httpClient, other.httpClient) && this.port == other.port;
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(httpClient);
            result = 31 * result + Integer.hashCode(port);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>httpClient</code>、<code>port</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "JavalinContractClient[httpClient=" + httpClient + ", port=" + port + "]";
        }
    }
}
