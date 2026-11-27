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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** \u4e3a Quarkus \u901a\u7528 Web \u5408\u540c\u66b4\u9732\u53ef\u8bf7\u6c42\u7684 JAX-RS \u8d44\u6e90\u3002 */
@Path("/contract")
@Produces(MediaType.APPLICATION_JSON)
public class Ddd4jQuarkusContractResource {

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/success")
    public R<Map<String, String>> success() {
        return R.ok(java.util.Collections.singletonMap("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/public")
    public R<Map<String, String>> publicEndpoint() {
        return R.ok(java.util.Collections.singletonMap("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/protected")
    public R<Map<String, String>> protectedEndpoint() {
        return R.ok(java.util.Collections.singletonMap("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>Response.status()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param ignoredBody \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>String</code> \u53c2\u6570 <code>ignoredBody</code>
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Response.status(Response.Status.CREATED).entity(R.ok(java.util.Collections.singletonMap("result", "created"))).build()</code>
     */
    @POST
    @Path("/created")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response created(String ignoredBody) {
        return Response.status(Response.Status.CREATED)
                .entity(R.ok(java.util.Collections.singletonMap("result", "created"))).build();
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(data)</code>
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
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param ignoredBody \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>String</code> \u53c2\u6570 <code>ignoredBody</code>
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(java.util.Collections.singletonMap("result", "accepted"))</code>
     */
    @POST
    @Path("/idempotent")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Map<String, String>> idempotent(String ignoredBody) {
        return R.ok(java.util.Collections.singletonMap("result", "accepted"));
    }

    /**
     * \u6784\u9020 <code>IllegalArgumentException</code>\u3001<code>SecurityException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u8be5\u5f02\u5e38\u8def\u5f84\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u7ed3\u679c
     */
    @GET
    @Path("/errors/{type}")
    public R<Void> error(@PathParam("type") String type) {
        switch (type) {
            case "bad-request": throw new IllegalArgumentException("bad request");
            case "forbidden": throw new SecurityException("forbidden");
            case "not-found": throw new NoSuchElementException("not found");
            case "conflict": throw new IllegalStateException("conflict");
            case "unsupported-media-type": throw new WebStatusException(415, "unsupported media type");
            case "unprocessable-entity": throw new WebStatusException(422, "unprocessable entity");
            case "too-many-requests": throw new WebStatusException(429, "too many requests");
            default: throw new RuntimeException("internal failure");
        }
    }
}
