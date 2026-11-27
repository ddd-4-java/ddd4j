package io.ddd4j.web.quarkus;

import io.ddd4j.core.api.R;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.error.WebStatusException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/public")
    public R<Map<String, String>> publicEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    /**
     * \u901a\u8fc7 <code>R.ok()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>R.ok(java.util.Collections.singletonMap("result", "ok"))</code>
     */
    @GET
    @Path("/protected")
    public R<Map<String, String>> protectedEndpoint() {
        return R.ok(Map.of("result", "ok"));
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
                .entity(R.ok(Map.of("result", "created"))).build();
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
        return R.ok(Map.of("result", "accepted"));
    }

    /**
     * \u6784\u9020 <code>IllegalArgumentException</code>\u3001<code>SecurityException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u8be5\u5f02\u5e38\u8def\u5f84\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u7ed3\u679c
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
