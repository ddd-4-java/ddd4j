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
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@HelidonTest
@AddBean(Ddd4jHelidonWebContractTest.ContractApplication.class)
@AddConfig(key = "ddd4j.web.public-paths", value = "/contract/success,/contract/public,/contract/created,"
        + "/contract/context,/contract/idempotent,/contract/errors/**")
@AddConfig(key = "ddd4j.web.idempotency.cache-name", value = "helidon-contract")
class Ddd4jHelidonWebContractTest extends AbstractWebContractTest {

    @Inject
    private WebTarget target;

    private final WebContractClient contractClient = new HelidonContractClient();

    @BeforeEach
    void setUp() {
        CacheKit.build("helidon-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("helidon-contract");
    }

    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    private final class HelidonContractClient implements WebContractClient {

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

    @ApplicationPath("/")
    public static class ContractApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(ContractResource.class,
                    Ddd4jHelidonRequestFilter.class,
                    Ddd4jHelidonResponseFilter.class,
                    Ddd4jHelidonExceptionMapper.class,
                    Ddd4jHelidonIllegalStateExceptionMapper.class);
        }
    }

    @Path("/contract")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class ContractResource {

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
}
