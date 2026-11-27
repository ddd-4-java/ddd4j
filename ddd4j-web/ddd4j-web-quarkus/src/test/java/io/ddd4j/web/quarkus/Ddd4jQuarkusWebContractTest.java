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

/** \u5b9e\u73b0 <code>AbstractWebContractTest</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>setUp()</code>\u3001<code>tearDown()</code>\u3001<code>client()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
@QuarkusTest
class Ddd4jQuarkusWebContractTest extends AbstractWebContractTest {

    /** \u6267\u884c\u771f\u5b9e\u8bf7\u6c42\u4f9b\u516c\u5171 HTTP \u5408\u540c\u65ad\u8a00\u4f7f\u7528\u7684\u5ba2\u6237\u7aef\u3002 */
    private final WebContractClient contractClient = new QuarkusContractClient();
    /** \u8fde\u63a5\u9694\u79bb\u6d4b\u8bd5\u5e94\u7528\u7684\u57fa\u7840 URI\u3002 */
    @TestHTTPResource("/")
    URI baseUri;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>CacheKit.build()</code>\u3001<code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>provider()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        CacheKit.build("quarkus-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
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
    private final class QuarkusContractClient implements WebContractClient {

        /** \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
        private final HttpClient httpClient = HttpClient.newHttpClient();

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
