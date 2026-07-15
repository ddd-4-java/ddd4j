package io.ddd4j.web.quarkus;

import io.ddd4j.core.api.R;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebStatusException;
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

@Path("/contract")
@Produces(MediaType.APPLICATION_JSON)
public class Ddd4jQuarkusContractResource {

    @GET
    @Path("/success")
    public R<Map<String, String>> success() {
        return R.ok(Map.of("result", "ok"));
    }

    @GET
    @Path("/public")
    public R<Map<String, String>> publicEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    @GET
    @Path("/protected")
    public R<Map<String, String>> protectedEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    @POST
    @Path("/created")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response created(String ignoredBody) {
        return Response.status(Response.Status.CREATED)
                .entity(R.ok(Map.of("result", "created"))).build();
    }

    @GET
    @Path("/context")
    public R<Map<String, Object>> context() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
        data.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
        data.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
        return R.ok(data);
    }

    @POST
    @Path("/idempotent")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Map<String, String>> idempotent(String ignoredBody) {
        return R.ok(Map.of("result", "accepted"));
    }

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
