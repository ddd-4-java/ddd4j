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
package io.ddd4j.web.quarkus;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractResponse;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实现 <code>AbstractWebContractTest</code>，供测试调用 <code>setUp()</code>、<code>tearDown()</code>、<code>client()</code> 并观察结果。
 */
@QuarkusTest
class Ddd4jQuarkusWebContractTest extends AbstractWebContractTest {

    /**
     * 执行真实请求供公共 HTTP 合同断言使用的客户端。
     */
    private final WebContractClient contractClient = new QuarkusContractClient();
    /**
     * 连接隔离测试应用的基础 URI。
     */
    @TestHTTPResource("/")
    URI baseUri;

    /**
     * 测试执行前执行 <code>CacheKit.build()</code>、<code>mock()</code>、<code>BaseContext.inject()</code>、<code>provider()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        CacheKit.build("quarkus-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    /**
     * 测试结束后执行 <code>ThreadContext.clear()</code>、<code>BaseContext.clear()</code>、<code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("quarkus-contract");
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
     * 在当前 HTTP 框架中执行共用合同的客户端适配器。
     */
    private final class QuarkusContractClient implements WebContractClient {

        /**
         * 用于发送 HTTP 请求的客户端。
         */
        private final HttpClient httpClient = HttpClient.newHttpClient();

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
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path));
            headers.forEach(builder::header);
            HttpRequest.BodyPublisher publisher = Objects.isNull(body)
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            if (Objects.nonNull(body)) {
                builder.header("Content-Type", "application/json");
            }
            try {
                HttpResponse<String> response = httpClient.send(builder.method(method, publisher).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                Map<String, List<String>> responseHeaders = response.headers().map();
                return new WebContractResponse(response.statusCode(), responseHeaders, response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Quarkus contract request was interrupted", exception);
            } catch (IOException exception) {
                throw new IllegalStateException("Quarkus contract request failed", exception);
            }
        }
    }
}
