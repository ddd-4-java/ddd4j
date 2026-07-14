package io.ddd4j.web.dropwizard;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.CacheIdempotencyGuard;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebAccessPolicy;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.ddd4j.web.core.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jDropwizardWebContractTest extends AbstractWebContractTest {

    private static final Ddd4jDropwizardRequestFilter REQUEST_FILTER = new Ddd4jDropwizardRequestFilter(
            new WebRequestContextFactory(),
            new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                    WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path))),
            new WebIdempotencyLifecycle(new CacheIdempotencyGuard("dropwizard-contract")));

    @RegisterExtension
    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new ContractResource())
            .addProvider(REQUEST_FILTER)
            .addProvider(new Ddd4jDropwizardResponseFilter())
            .addProvider(new Ddd4jDropwizardExceptionMapper(new DefaultWebExceptionTranslator()))
            .build();

    private final WebContractClient contractClient = new DropwizardContractClient();

    @BeforeEach
    void setUp() {
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
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

    private static final class DropwizardContractClient implements WebContractClient {

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            jakarta.ws.rs.client.Invocation.Builder builder = RESOURCES.target(path).request();
            headers.forEach(builder::header);
            Response response = body == null
                    ? builder.method(method)
                    : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
            try (response) {
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                response.getStringHeaders().forEach((name, values) -> responseHeaders.put(name, List.copyOf(values)));
                return new WebContractResponse(response.getStatus(), responseHeaders, response.readEntity(String.class));
            }
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
