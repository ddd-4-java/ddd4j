package io.ddd4j.web.dropwizard;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled("Dropwizard testing JUnit 5 版本不兼容（NoSuchMethodError: ReflectionUtils.makeAccessible）")
@ExtendWith(DropwizardExtensionsSupport.class)
class Ddd4jDropwizardWebContractTest extends AbstractWebContractTest {

    private static final Ddd4jDropwizardRequestFilter REQUEST_FILTER = new Ddd4jDropwizardRequestFilter(
            new WebRequestContextFactory(),
            new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                    WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path))),
            new WebIdempotencyLifecycle(new CacheIdempotencyGuard("dropwizard-contract")));

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new ContractResource())
            .addProvider(REQUEST_FILTER)
            .addProvider(new Ddd4jDropwizardResponseFilter())
            .addProvider(new Ddd4jDropwizardExceptionMapper(new DefaultWebExceptionTranslator()))
            .addProvider(new Ddd4jDropwizardIllegalStateExceptionMapper())
            .build();

    private final WebContractClient contractClient = new DropwizardContractClient();

    @BeforeEach
    void setUp() {
        CacheKit.build("dropwizard-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("dropwizard-contract");
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
            javax.ws.rs.client.Invocation.Builder builder = RESOURCES.target(path).request();
            headers.forEach(builder::header);
            Response response = Objects.isNull(body)
                    ? builder.method(method)
                    : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
            try {
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                response.getStringHeaders().forEach((name, values) -> responseHeaders.put(name, Collections.unmodifiableList(new java.util.ArrayList<>(values))));
                WebContractResponse contractResponse = new WebContractResponse(response.getStatus(), responseHeaders, response.readEntity(String.class));
                response.close();
                return contractResponse;
            } catch (Exception processingError) {
                throw new IllegalStateException("Dropwizard contract response processing failed", processingError);
            }
        }
    }

    @Path("/contract")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class ContractResource {

        @GET
        @Path("/success")
        public R<Map<String, String>> success() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @GET
        @Path("/public")
        public R<Map<String, String>> publicEndpoint() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @GET
        @Path("/protected")
        public R<Map<String, String>> protectedEndpoint() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @POST
        @Path("/created")
        @Consumes(MediaType.APPLICATION_JSON)
        public Response created(String ignoredBody) {
            return Response.status(Response.Status.CREATED)
                    .entity(R.ok(Collections.singletonMap("result", "created"))).build();
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
            return R.ok(Collections.singletonMap("result", "accepted"));
        }

        @GET
        @Path("/errors/{type}")
        public R<Void> error(@PathParam("type") String type) {
            RuntimeException __ex;
            switch (type) {
                case "bad-request": __ex = new IllegalArgumentException("bad request"); break;
                case "forbidden": __ex = new SecurityException("forbidden"); break;
                case "not-found": __ex = new NoSuchElementException("not found"); break;
                case "conflict": __ex = new IllegalStateException("conflict"); break;
                case "unsupported-media-type": __ex = new WebStatusException(415, "unsupported media type"); break;
                case "unprocessable-entity": __ex = new WebStatusException(422, "unprocessable entity"); break;
                case "too-many-requests": __ex = new WebStatusException(429, "too many requests"); break;
                default: __ex = new RuntimeException("internal failure"); break;
            }
            throw __ex;
        }
    }
}
