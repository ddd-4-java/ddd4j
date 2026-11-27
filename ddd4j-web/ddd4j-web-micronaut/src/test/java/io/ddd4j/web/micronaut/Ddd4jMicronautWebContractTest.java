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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u901a\u8fc7 <code>contractClient.request()</code>\u3001<code>response.status()</code>\u3001<code>response.body()</code> \u9a8c\u8bc1 <code>Ddd4jMicronautWebContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jMicronautWebContractTest extends AbstractWebContractTest {

    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private EmbeddedServer server;
    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private WebContractClient contractClient;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>ApplicationContext.run()</code>\u3001<code>BaseContext.inject()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        CacheKit.build("micronaut-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("micronaut.server.port", -1);
        properties.put("ddd4j.web.public-paths", Arrays.asList(
                WebContractPaths.SUCCESS,
                WebContractPaths.CREATED,
                WebContractPaths.PUBLIC,
                WebContractPaths.CONTEXT,
                WebContractPaths.IDEMPOTENT,
                "/contract/async-context",
                "/contract/errors/**"));
        properties.put("ddd4j.web.idempotency-cache-name", "micronaut-contract");
        server = ApplicationContext.run(EmbeddedServer.class, properties);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        contractClient = new MicronautContractClient(server.getPort());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>server.close()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
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

    /** \u9a8c\u8bc1<code>response.status()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.body().contains("tenant-async")</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldPropagateTenantAcrossReactorScheduler() {
        WebContractResponse response = contractClient.request("GET", "/contract/async-context",
                Collections.singletonMap("X-Tenant-Id", "tenant-async"), null);

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
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    /** \u5728\u5f53\u524d HTTP \u6846\u67b6\u4e2d\u6267\u884c\u5171\u7528\u5408\u540c\u7684\u5ba2\u6237\u7aef\u9002\u914d\u5668\u3002 */
    private static final class MicronautContractClient implements WebContractClient {
        /** \u5f53\u524d\u5939\u5177\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
        private final int port;

        /**
         * 初始化 <code>MicronautContractClient</code> 使用的 <code>port</code>。
         *
         * @param port <code>port</code> \u5b57\u6bb5
         */
        MicronautContractClient(int port) {
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
                throw new IllegalStateException("Micronaut contract request failed", exception);
            }
        }
    }
}

/** \u7531\u5e94\u7528\u6846\u67b6\u6ce8\u518c\u3001\u4f9b\u6d4b\u8bd5\u53d1\u8d77\u771f\u5b9e\u8bf7\u6c42\u7684\u63a7\u5236\u5668\u3002 */
@Controller("/contract")
final class MicronautContractController {

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/success")
    R<Map<String, String>> success() {
        return R.ok(Collections.singletonMap("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/public")
    R<Map<String, String>> publicEndpoint() {
        return R.ok(Collections.singletonMap("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(Map.of("result", "ok"))</code>
     */
    @Get("/protected")
    R<Map<String, String>> protectedEndpoint() {
        return R.ok(Collections.singletonMap("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>HttpResponse.created()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>HttpResponse.created(R.ok(Map.of("result", "created")))</code>
     */
    @Post("/created")
    HttpResponse<R<Map<String, String>>> created() {
        return HttpResponse.created(R.ok(Collections.singletonMap("result", "created")));
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
        return R.ok(Collections.singletonMap("result", "accepted"));
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
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tenantId", tenantId);
            return R.ok(data);
        });
    }

    /**
     * \u6784\u9020 <code>IllegalStateException</code>\u3001<code>IllegalArgumentException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u8be5\u5f02\u5e38\u8def\u5f84\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u7ed3\u679c
     */
    @Get("/errors/{type}")
    R<Void> error(String type) {
        RuntimeException ex;
        switch (type) {
            case "bad-request":
                ex = new IllegalArgumentException("bad request");
                break;
            case "forbidden":
                ex = new SecurityException("forbidden");
                break;
            case "not-found":
                ex = new NoSuchElementException("not found");
                break;
            case "conflict":
                ex = new IllegalStateException("conflict");
                break;
            case "unsupported-media-type":
                ex = new WebStatusException(415, "unsupported media type");
                break;
            case "unprocessable-entity":
                ex = new WebStatusException(422, "unprocessable entity");
                break;
            case "too-many-requests":
                ex = new WebStatusException(429, "too many requests");
                break;
            default:
                ex = new RuntimeException("internal failure");
                break;
        }
        throw ex;
    }
}
