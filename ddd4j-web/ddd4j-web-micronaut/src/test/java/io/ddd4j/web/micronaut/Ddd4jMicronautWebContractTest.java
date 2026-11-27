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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通过 <code>contractClient.request()</code>、<code>response.status()</code>、<code>response.body()</code> 验证 <code>Ddd4jMicronautWebContract</code> 的运行行为。
 */
class Ddd4jMicronautWebContractTest extends AbstractWebContractTest {

    /**
     * 当前测试启动并负责释放的本地服务。
     */
    private EmbeddedServer server;
    /**
     * 执行真实请求供公共 HTTP 合同断言使用的客户端。
     */
    private WebContractClient contractClient;

    /**
     * 测试执行前执行 <code>CacheKit.build()</code>、<code>mock()</code>、<code>ApplicationContext.run()</code>、<code>BaseContext.inject()</code> 完成资源准备。
     */
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

    /**
     * 测试结束后执行 <code>server.close()</code>、<code>ThreadContext.clear()</code>、<code>BaseContext.clear()</code>、<code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.close();
        }
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("micronaut-contract");
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
     * 验证<code>response.status()</code> 等于 <code>200</code>，<code>response.body().contains("tenant-async")</code>为真。
     */
    @Test
    void shouldPropagateTenantAcrossReactorScheduler() {
        WebContractResponse response = contractClient.request("GET", "/contract/async-context",
                Map.of("X-Tenant-Id", "tenant-async"), null);

        assertEquals(200, response.status(), response.body());
        assertTrue(response.body().contains("tenant-async"));
    }

    /**
     * 验证<code>response.status()</code> 等于 <code>409</code>，<code>response.body().contains("inventory is locked\\r\\ntry again later")</code>为真。
     */
    @Test
    void multilineErrorKeepsHttpStatusAndJsonMessage() {
        WebContractResponse response = contractClient.request("GET", "/contract/errors/multiline",
                java.util.Collections.emptyMap(), null);

        assertEquals(409, response.status(), response.body());
        assertTrue(response.body().contains("inventory is locked\\r\\ntry again later"), response.body());
    }

    /**
     * 验证<code>connection.getResponseCode()</code> 等于 <code>409</code>，<code>reason.length()</code> 等于 <code>"Conflict".length()</code>，<code>reason</code> 等于 <code>"Conflict"</code>。
     *
     * @throws java.io.IOException 读取或写入测试数据时发生 I/O 错误
     */
    @Test
    void longErrorKeepsStatusLineAndFullJsonMessage() throws java.io.IOException {
        URL url = new URL("http://127.0.0.1:" + server.getPort() + "/contract/errors/long");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            assertEquals(409, connection.getResponseCode());
            String reason = connection.getResponseMessage();
            assertEquals("Conflict".length(), reason.length(), "status line must carry the standard reason");
            assertEquals("Conflict", reason);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                String json = reader.lines().collect(Collectors.joining());
                assertTrue(json.contains("\"msg\":\"" + MicronautContractController.LONG_ERROR + "\""),
                        "response must contain the complete error message");
            }
        } finally {
            connection.disconnect();
        }
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
    private final static class MicronautContractClient implements WebContractClient {

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
         * 用给定值初始化 <code>MicronautContractClient</code> 实例。
         *
         * @param httpClient 用于发送 HTTP 请求的客户端
         * @param port       <code>port</code> 字段
         */
        @JsonCreator()
        private MicronautContractClient(@JsonProperty("httpClient") HttpClient httpClient, @JsonProperty("port") int port) {
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
}

/**
 * 由应用框架注册、供测试发起真实请求的控制器。
 */
@Controller("/contract")
final class MicronautContractController {
    /**
     * 验证过长异常原因截断行为的输入值。
     */
    static final String LONG_ERROR = "inventory is locked " + String.join("", java.util.Collections.nCopies(5000, "x"));


    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/success")
    R<Map<String, String>> success() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/public")
    R<Map<String, String>> publicEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/protected")
    R<Map<String, String>> protectedEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * 通过 <code>HttpResponse.created()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>HttpResponse.created(R.ok(Map.of("result", "created")))</code>
     */
    @Post("/created")
    HttpResponse<R<Map<String, String>>> created() {
        return HttpResponse.created(R.ok(Map.of("result", "created")));
    }

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(data)</code>
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
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "accepted"))</code>
     */
    @Post("/idempotent")
    R<Map<String, String>> idempotent() {
        return R.ok(Map.of("result", "accepted"));
    }

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("tenantId", tenantId))</code>
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
     * 构造 <code>IllegalStateException</code>、<code>IllegalArgumentException</code> 作为当前测试的输入或结果。
     *
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     * @return 该异常路径没有正常返回结果
     */
    @Get("/errors/{type}")
    R<Void> error(String type) {
        if ("long".equals(type)) {
            throw new IllegalStateException(LONG_ERROR);
        }
        if ("multiline".equals(type)) {
            throw new IllegalStateException("inventory is locked\r\ntry again later");
        }
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
