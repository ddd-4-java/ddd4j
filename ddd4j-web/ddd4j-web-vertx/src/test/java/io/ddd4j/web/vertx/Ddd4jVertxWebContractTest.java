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
package io.ddd4j.web.vertx;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实现 <code>AbstractWebContractTest</code>，供测试调用 <code>setUp()</code>、<code>tearDown()</code>、<code>client()</code> 并观察结果。
 */
class Ddd4jVertxWebContractTest extends AbstractWebContractTest {

    /**
     * 由当前测试创建和释放的 Vert.x 运行环境。
     */
    private Vertx vertx;
    /**
     * 当前测试启动并负责释放的本地服务。
     */
    private HttpServer server;
    /**
     * 执行真实请求供公共 HTTP 合同断言使用的客户端。
     */
    private WebContractClient contractClient;

    /**
     * 测试执行前执行 <code>CacheKit.build()</code>、<code>mock()</code>、<code>BaseContext.inject()</code>、<code>provider()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        CacheKit.build("vertx-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
        new Ddd4jVertxWeb(new WebRequestContextFactory(), requestLifecycle,
                new DefaultWebExceptionTranslator(),
                new WebIdempotencyLifecycle(new CacheIdempotencyGuard("vertx-contract")),
                io.vertx.core.json.Json::encode).install(router);
        registerContractRoutes(router);
        server = vertx.createHttpServer().requestHandler(router).listen(0)
                .toCompletionStage().toCompletableFuture().join();
        contractClient = new VertxContractClient(HttpClient.newHttpClient(), server.actualPort());
    }

    /**
     * 测试结束后执行 <code>server.close()</code>、<code>vertx.close()</code>、<code>BaseContext.clear()</code>、<code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.close().toCompletionStage().toCompletableFuture().join();
        }
        if (Objects.nonNull(vertx)) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
        BaseContext.clear();
        CacheKit.unregister("vertx-contract");
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
     * 调用 <code>router.get()</code>、<code>context.json()</code>、<code>R.ok()</code> 执行当前夹具操作。
     *
     * @param router 传给 <code>router.get()</code> 的 <code>Router</code> 输入
     */
    private void registerContractRoutes(Router router) {
        router.get(WebContractPaths.SUCCESS).handler(context -> context.json(R.ok(Map.of("result", "ok"))));
        router.get(WebContractPaths.PUBLIC).handler(context -> context.json(R.ok(Map.of("result", "ok"))));
        router.get(WebContractPaths.PROTECTED).handler(context -> context.json(R.ok(Map.of("result", "ok"))));
        router.post(WebContractPaths.CREATED).handler(context -> {
            context.response().setStatusCode(201);
            context.json(R.ok(Map.of("result", "created")));
        });
        router.get(WebContractPaths.CONTEXT).handler(context -> {
            Map<String, Object> requestContext = new LinkedHashMap<>();
            Ddd4jVertxContext.request(context).ifPresent(value -> {
                requestContext.put("requestId", value.requestId());
                requestContext.put("traceId", value.traceId());
                requestContext.put("tenantId", value.tenantId());
            });
            context.json(R.ok(requestContext));
        });
        router.post(WebContractPaths.IDEMPOTENT)
                .handler(context -> context.json(R.ok(Map.of("result", "accepted"))));
        router.get("/contract/errors/:type").handler(context -> context.fail(error(context.pathParam("type"))));
    }

    /**
     * 构造 <code>IllegalArgumentException</code>、<code>SecurityException</code> 作为当前测试的输入或结果。
     *
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     * @return 提供给后续契约断言的 <code>Throwable</code> 结果
     */
    private Throwable error(String type) {
        return switch (type) {
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
    private final static class VertxContractClient implements WebContractClient {

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
         * 用给定值初始化 <code>VertxContractClient</code> 实例。
         *
         * @param httpClient 用于发送 HTTP 请求的客户端
         * @param port       <code>port</code> 字段
         */
        @JsonCreator()
        private VertxContractClient(@JsonProperty("httpClient") HttpClient httpClient, @JsonProperty("port") int port) {
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
                HttpResponse<String> response = send(builder.method(method, publisher).build());
                return new WebContractResponse(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Vert.x contract request interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("Vert.x contract request failed", exception);
            }
        }

        /**
         * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
         *
         * @param request 待交给被测服务处理的请求
         * @return 当前测试使用的结果，取自 <code>httpClient.send(request, HttpResponse.BodyHandlers.ofString())</code>
         * @throws IOException          读取或写入测试数据时发生 I/O 错误
         * @throws InterruptedException 等待测试操作时被中断
         */
        private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException firstFailure) {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
            VertxContractClient other = (VertxContractClient) obj;
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
            return "VertxContractClient[httpClient=" + httpClient + ", port=" + port + "]";
        }
    }
}
