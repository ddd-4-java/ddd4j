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
package io.ddd4j.web.micronaut;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;

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
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
class Ddd4jMicronautWebContractTest extends AbstractWebContractTest {

    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private EmbeddedServer server;
    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private WebContractClient contractClient;

    /**
     * 基于 JDK HTTP 客户端的契约客户端，通过随机端口访问本地测试实例。
     *
     * @param httpClient 发起请求使用的 HTTP 客户端
     * @param port 测试实例监听的端口
     */
    private final static class MicronautContractClient implements WebContractClient {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
        private final HttpClient httpClient;

        /** \u5f53\u524d\u5939\u5177\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
        private final int port;

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
                HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path));
                headers.forEach(builder::header);
                HttpRequest.BodyPublisher publisher = Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
                java.net.http.HttpResponse<String> response = httpClient.send(builder.method(method, publisher).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                return new WebContractResponse(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Micronaut contract request interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("Micronaut contract request failed", exception);
            }
        }

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>MicronautContractClient</code> \u5b9e\u4f8b\u3002
         * @param httpClient \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef
         * @param port <code>port</code> \u5b57\u6bb5
         */
        @JsonCreator()
        private MicronautContractClient(@JsonProperty("httpClient") HttpClient httpClient, @JsonProperty("port") int port) {
            this.httpClient = httpClient;
            this.port = port;
        }

        /**
         * \u8bfb\u53d6\u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002
         * @return \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef
         */
        @JsonProperty("httpClient")
        public HttpClient httpClient() {
            return httpClient;
        }

        /**
         * \u8bfb\u53d6<code>port</code> \u5b57\u6bb5\u3002
         * @return <code>port</code> \u5b57\u6bb5
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
            MicronautContractClient other = (MicronautContractClient) obj;
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
            return "MicronautContractClient[httpClient=" + httpClient + ", port=" + port + "]";
        }
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>ApplicationContext.run()</code>\u3001<code>BaseContext.inject()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        CacheKit.build("micronaut-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));

        Map<String, Object> properties = Map.of(
                "micronaut.server.port", -1,
                "ddd4j.web.public-paths", List.of(
                        WebContractPaths.SUCCESS,
                        WebContractPaths.CREATED,
                        WebContractPaths.PUBLIC,
                        WebContractPaths.CONTEXT,
                        WebContractPaths.IDEMPOTENT,
                        "/contract/async-context",
                        "/contract/errors/**"),
                "ddd4j.web.idempotency-cache-name", "micronaut-contract");
        server = ApplicationContext.run(EmbeddedServer.class, properties);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        contractClient = new MicronautContractClient(HttpClient.newHttpClient(), server.getPort());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>server.close()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.close();
        }
        ThreadContext.clear();
        /**
         * BaseContext.clear() 方法。
         */
        BaseContext.clear();
        CacheKit.unregister("micronaut-contract");
    }

    /**
     * client() 方法。
     * @return 方法执行结果。
     */
    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    /** \u9a8c\u8bc1<code>response.status()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.body().contains("tenant-async")</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldPropagateTenantAcrossReactorScheduler() {
        WebContractResponse response = contractClient.request("GET", "/contract/async-context",
                /**
                 * Map.of() 方法。
                 * @param "X-Tenant-Id" "X-Tenant-Id" 参数。
                 * @param "tenant-async" "tenant-async" 参数。
                 */
                Map.of("X-Tenant-Id", "tenant-async"), null);

        assertEquals(200, response.status(), response.body());
        assertTrue(response.body().contains("tenant-async"));
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
}

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
@Controller("/contract")
final class MicronautContractController {

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/success")
    R<Map<String, String>> success() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/public")
    R<Map<String, String>> publicEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/protected")
    R<Map<String, String>> protectedEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>HttpResponse.created()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>HttpResponse.created(R.ok(Map.of("result", "created")))</code>
     */
    @Post("/created")
    HttpResponse<R<Map<String, String>>> created() {
        return HttpResponse.created(R.ok(Map.of("result", "created")));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(data)</code>
     */
    @Get("/context")
    R<Map<String, Object>> context() {
        WebRequestContext requestContext = Ddd4jMicronautContext.current()
                .orElseThrow(() -> new IllegalStateException("Micronaut request context is missing"))
                .requestContext();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestContext.requestId());
        data.put("traceId", requestContext.traceId());
        data.put("tenantId", requestContext.tenantId());
        return R.ok(data);
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "accepted"))</code>
     */
    @Post("/idempotent")
    R<Map<String, String>> idempotent() {
        return R.ok(Map.of("result", "accepted"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("tenantId", tenantId))</code>
     */
    @Get("/async-context")
    Mono<R<Map<String, Object>>> asyncContext() {
        return Mono.delay(Duration.ofMillis(10)).publishOn(Schedulers.boundedElastic()).map(ignored -> {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (Objects.isNull(tenantId)) {
                throw new IllegalStateException("Micronaut async tenant context is missing");
            }
            return R.ok(Map.of("tenantId", tenantId));
        });
    }

    /**
     * \u6784\u9020 <code>IllegalStateException</code>\u3001<code>IllegalArgumentException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u8be5\u5f02\u5e38\u8def\u5f84\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u7ed3\u679c
     */
    @Get("/errors/{type}")
    R<Void> error(String type) {
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
