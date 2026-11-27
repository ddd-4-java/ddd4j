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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u5b9e\u73b0 <code>AbstractWebContractTest</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>setUp()</code>\u3001<code>tearDown()</code>\u3001<code>client()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
class Ddd4jVertxWebContractTest extends AbstractWebContractTest {

    /** \u7531\u5f53\u524d\u6d4b\u8bd5\u521b\u5efa\u548c\u91ca\u653e\u7684 Vert.x \u8fd0\u884c\u73af\u5883\u3002 */
    private Vertx vertx;
    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private HttpServer server;
    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private WebContractClient contractClient;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>provider()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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
        contractClient = new VertxContractClient(server.actualPort());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>server.close()</code>\u3001<code>vertx.close()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
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
     * \u8c03\u7528 <code>router.get()</code>\u3001<code>context.json()</code>\u3001<code>R.ok()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param router \u4f20\u7ed9 <code>router.get()</code> \u7684 <code>Router</code> \u8f93\u5165
     */
    private void registerContractRoutes(Router router) {
        router.get(WebContractPaths.SUCCESS).handler(context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        router.get(WebContractPaths.PUBLIC).handler(context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        router.get(WebContractPaths.PROTECTED).handler(context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        router.post(WebContractPaths.CREATED).handler(context -> {
            context.response().setStatusCode(201);
            context.json(R.ok(Collections.singletonMap("result", "created")));
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
                .handler(context -> context.json(R.ok(Collections.singletonMap("result", "accepted"))));
        router.get("/contract/errors/:type").handler(context -> context.fail(error(context.pathParam("type"))));
    }

    /**
     * \u6784\u9020 <code>IllegalArgumentException</code>\u3001<code>SecurityException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Throwable</code> \u7ed3\u679c
     */
    private Throwable error(String type) {
        switch (type) {
            case "bad-request":
                return new IllegalArgumentException("bad request");
            case "forbidden":
                return new SecurityException("forbidden");
            case "not-found":
                return new NoSuchElementException("not found");
            case "conflict":
                return new IllegalStateException("conflict");
            case "unsupported-media-type":
                return new WebStatusException(415, "unsupported media type");
            case "unprocessable-entity":
                return new WebStatusException(422, "unprocessable entity");
            case "too-many-requests":
                return new WebStatusException(429, "too many requests");
            default:
                return new RuntimeException("internal failure");
        }
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

    /** \u5728\u5f53\u524d HTTP \u6846\u67b6\u4e2d\u6267\u884c\u5171\u7528\u5408\u540c\u7684\u5ba2\u6237\u7aef\u9002\u914d\u5668\u3002 */
    private static final class VertxContractClient implements WebContractClient {
        /** \u5f53\u524d\u5939\u5177\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
        private final int port;

        /**
         * 初始化 <code>VertxContractClient</code> 使用的 <code>port</code>。
         *
         * @param port <code>port</code> \u5b57\u6bb5
         */
        VertxContractClient(int port) {
            this.port = port;
        }

        /**
         * 通过当前客户端发起 HTTP 请求。
         *
         * <p>此实现更新<code>responseBody = ""</code>。</p>
         *
         * @param method HTTP 方法
         * @param path 请求路径
         * @param headers 请求头
         * @param body 请求体
         * @return 响应快照
         */
        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            try {
                URL url = new URL("http://127.0.0.1:" + port + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
                if (body != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int statusCode = conn.getResponseCode();
                Map<String, java.util.List<String>> responseHeaders = new LinkedHashMap<>();
                for (Map.Entry<String, java.util.List<String>> entry : conn.getHeaderFields().entrySet()) {
                    if (Objects.nonNull(entry.getKey())) {
                        responseHeaders.put(entry.getKey(), entry.getValue());
                    }
                }
                String responseBody;
                InputStream responseStream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (Objects.isNull(responseStream)) {
                    responseBody = "";
                } else {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                        responseBody = reader.lines().collect(Collectors.joining("\n"));
                    }
                }
                conn.disconnect();
                return new WebContractResponse(statusCode, responseHeaders, responseBody);
            } catch (Exception exception) {
                throw new IllegalStateException("Vert.x contract request failed", exception);
            }
        }
    }
}
