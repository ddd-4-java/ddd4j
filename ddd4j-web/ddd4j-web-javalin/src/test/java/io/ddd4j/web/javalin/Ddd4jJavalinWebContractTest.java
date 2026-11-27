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

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.health.ReadinessEndpoint;
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
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>Ddd4jJavalinWeb</code> \u5728<code>readinessEndpointReflectsRegistry</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jJavalinWebContractTest extends AbstractWebContractTest {

    /** \u7531\u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u548c\u5173\u95ed\u7684\u5e94\u7528\u5b9e\u4f8b\u3002 */
    private Javalin app;
    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private WebContractClient contractClient;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private RuntimeReadinessRegistry readinessRegistry;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>provider()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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
        app = Javalin.create(config -> config.showJavalinBanner = false);
        ddd4jWeb.configure(app);
        registerContractRoutes(app);
        app.start(0);
        contractClient = new JavalinContractClient(app.port());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>app.stop()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("javalin-contract");
    }

    /** {@inheritDoc} */
    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    /** \u9a8c\u8bc1<code>ready.status()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>ready.body()</code> \u5305\u542b <code>"\"ready\":true"</code>\uff0c<code>unavailable.body()</code> \u5305\u542b <code>"\"ready\":false"</code>\u3002 */
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
     * 执行 <code>register Contract Routes</code>，调用 <code>javalinApp.get</code>、<code>context.json</code>、<code>R.ok</code>。
     *
     * @param javalinApp 传入此操作的 <code>Javalin</code> 类型 <code>javalinApp</code>
     */
    private void registerContractRoutes(Javalin javalinApp) {
        javalinApp.get(WebContractPaths.SUCCESS, context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        javalinApp.get(WebContractPaths.PUBLIC, context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        javalinApp.get(WebContractPaths.PROTECTED, context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        javalinApp.post(WebContractPaths.CREATED, context -> context.status(201)
                .json(R.ok(Collections.singletonMap("result", "created"))));
        javalinApp.get(WebContractPaths.CONTEXT, context -> {
            Map<String, Object> requestContext = new LinkedHashMap<>();
            requestContext.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            requestContext.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            requestContext.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            context.json(R.ok(requestContext));
        });
        javalinApp.post(WebContractPaths.IDEMPOTENT,
                context -> context.json(R.ok(Collections.singletonMap("result", "accepted"))));
        javalinApp.get("/contract/errors/{type}", context -> {
            String type = context.pathParam("type");
            Exception ex;
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
        });
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
    private static final class JavalinContractClient implements WebContractClient {
        /** \u5f53\u524d\u5939\u5177\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
        private final int port;

        /**
         * 初始化 <code>JavalinContractClient</code> 使用的 <code>port</code>。
         *
         * @param port <code>port</code> \u5b57\u6bb5
         */
        JavalinContractClient(int port) {
            this.port = port;
        }

        /** {@inheritDoc} */
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
                throw new IllegalStateException("Javalin contract request failed", exception);
            }
        }
    }
}
