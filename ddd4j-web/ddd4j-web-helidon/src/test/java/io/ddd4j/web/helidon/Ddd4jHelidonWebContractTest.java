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
package io.ddd4j.web.helidon;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractResponse;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实现 <code>AbstractWebContractTest</code>，供测试调用 <code>setUp()</code>、<code>tearDown()</code>、<code>client()</code> 并观察结果。
 */
@HelidonTest
@AddBean(Ddd4jHelidonWebContractTest.ContractApplication.class)
@AddConfig(key = "ddd4j.web.public-paths", value = "/contract/success,/contract/public,/contract/created,"
        + "/contract/context,/contract/idempotent,/contract/errors/**")
@AddConfig(key = "ddd4j.web.idempotency.cache-name", value = "helidon-contract")
class Ddd4jHelidonWebContractTest extends AbstractWebContractTest {

    /**
     * 执行真实请求供公共 HTTP 合同断言使用的客户端。
     */
    private final WebContractClient contractClient = new HelidonContractClient();
    /**
     * 当前映射、代理或反射调用需要更新的目标对象。
     */
    @Inject
    private WebTarget target;

    /**
     * 测试执行前执行 <code>CacheKit.build()</code>、<code>mock()</code>、<code>BaseContext.inject()</code>、<code>provider()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        CacheKit.build("helidon-contract", 300L);
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
        CacheKit.unregister("helidon-contract");
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
     * 将测试资源注册到实际运行容器的应用入口。
     */
    @ApplicationPath("/")
    public static class ContractApplication extends Application {

        /**
         * 提供 Helidon HTTP 合同所需的示例资源及标准请求过滤器。
         *
         * @return 由 Helidon 创建并注册的合同组件类型集合
         */
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(ContractResource.class,
                    Ddd4jHelidonRequestFilter.class,
                    Ddd4jHelidonResponseFilter.class,
                    Ddd4jHelidonExceptionMapper.class,
                    Ddd4jHelidonIllegalStateExceptionMapper.class);
        }
    }

    /**
     * 向测试容器注册 <code>success()</code>、<code>publicEndpoint()</code>、<code>protectedEndpoint()</code>、<code>created()</code> 等测试入口或组件。
     */
    @Path("/contract")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class ContractResource {

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
         */
        @GET
        @Path("/success")
        public R<Map<String, String>> success() {
            return R.ok(Map.of("result", "ok"));
        }

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
         */
        @GET
        @Path("/public")
        public R<Map<String, String>> publicEndpoint() {
            return R.ok(Map.of("result", "ok"));
        }

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "ok"))</code>
         */
        @GET
        @Path("/protected")
        public R<Map<String, String>> protectedEndpoint() {
            return R.ok(Map.of("result", "ok"));
        }

        /**
         * 通过 <code>Response.status()</code> 取得当前测试需要的结果。
         *
         * @param ignoredBody 本次调用使用的 <code>String</code> 参数 <code>ignoredBody</code>
         * @return 当前测试使用的结果，取自 <code>Response.status(Response.Status.CREATED).entity(R.ok(Map.of("result", "created"))).build()</code>
         */
        @POST
        @Path("/created")
        @Consumes(MediaType.APPLICATION_JSON)
        public Response created(String ignoredBody) {
            return Response.status(Response.Status.CREATED)
                    .entity(R.ok(Map.of("result", "created"))).build();
        }

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>R.ok(data)</code>
         */
        @GET
        @Path("/context")
        public R<Map<String, Object>> context() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            data.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            data.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            return R.ok(data);
        }

        /**
         * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
         *
         * @param ignoredBody 本次调用使用的 <code>String</code> 参数 <code>ignoredBody</code>
         * @return 当前测试使用的结果，取自 <code>R.ok(Map.of("result", "accepted"))</code>
         */
        @POST
        @Path("/idempotent")
        @Consumes(MediaType.APPLICATION_JSON)
        public R<Map<String, String>> idempotent(String ignoredBody) {
            return R.ok(Map.of("result", "accepted"));
        }

        /**
         * 构造 <code>IllegalArgumentException</code>、<code>SecurityException</code> 作为当前测试的输入或结果。
         *
         * @param type 用来解析、检查或构造测试对象的 Java 类型
         * @return 提供给后续契约断言的 <code>R&lt;Void&gt;</code> 结果
         */
        @GET
        @Path("/errors/{type}")
        public R<Void> error(@PathParam("type") String type) {
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

    /**
     * 在当前 HTTP 框架中执行共用合同的客户端适配器。
     */
    private final class HelidonContractClient implements WebContractClient {

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
            Invocation.Builder builder = target.path(path).request();
            headers.forEach(builder::header);
            Response response = Objects.isNull(body)
                    ? builder.method(method)
                    : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
            try (response) {
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                response.getStringHeaders().forEach((name, values) -> responseHeaders.put(name, List.copyOf(values)));
                return new WebContractResponse(response.getStatus(), responseHeaders, response.readEntity(String.class));
            }
        }
    }
}
