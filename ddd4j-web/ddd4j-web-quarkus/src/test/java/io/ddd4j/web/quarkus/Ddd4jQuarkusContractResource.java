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

import io.ddd4j.core.api.R;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.error.WebStatusException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 为 Quarkus 通用 Web 合同暴露可请求的 JAX-RS 资源。
 */
@Path("/contract")
@Produces(MediaType.APPLICATION_JSON)
public class Ddd4jQuarkusContractResource {

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/success")
    public R<Map<String, String>> success() {
        return R.ok(java.util.Collections.singletonMap("result", "ok"));
    }

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/public")
    public R<Map<String, String>> publicEndpoint() {
        return R.ok(java.util.Collections.singletonMap("result", "ok"));
    }

    /**
     * 通过 <code>R.ok()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/protected")
    public R<Map<String, String>> protectedEndpoint() {
        return R.ok(java.util.Collections.singletonMap("result", "ok"));
    }

    /**
     * 通过 <code>Response.status()</code> 取得当前测试需要的结果。
     *
     * @param ignoredBody 本次调用使用的 <code>String</code> 参数 <code>ignoredBody</code>
     * @return 当前测试使用的结果，取自 <code>Response.status(Response.Status.CREATED).entity(R.ok(java.util.Collections.singletonMap("result", "created"))).build()</code>
     */
    @POST
    @Path("/created")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response created(String ignoredBody) {
        return Response.status(Response.Status.CREATED)
                .entity(R.ok(java.util.Collections.singletonMap("result", "created"))).build();
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
     * @return 当前测试使用的结果，取自 <code>R.ok(java.util.Collections.singletonMap("result", "accepted"))</code>
     */
    @POST
    @Path("/idempotent")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Map<String, String>> idempotent(String ignoredBody) {
        return R.ok(java.util.Collections.singletonMap("result", "accepted"));
    }

    /**
     * 构造 <code>IllegalArgumentException</code>、<code>SecurityException</code> 作为当前测试的输入或结果。
     *
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     * @return 该异常路径没有正常返回结果
     */
    @GET
    @Path("/errors/{type}")
    public R<Void> error(@PathParam("type") String type) {
        switch (type) {
            case "bad-request":
                throw new IllegalArgumentException("bad request");
            case "forbidden":
                throw new SecurityException("forbidden");
            case "not-found":
                throw new NoSuchElementException("not found");
            case "conflict":
                throw new IllegalStateException("conflict");
            case "unsupported-media-type":
                throw new WebStatusException(415, "unsupported media type");
            case "unprocessable-entity":
                throw new WebStatusException(422, "unprocessable entity");
            case "too-many-requests":
                throw new WebStatusException(429, "too many requests");
            default:
                throw new RuntimeException("internal failure");
        }
    }
}
