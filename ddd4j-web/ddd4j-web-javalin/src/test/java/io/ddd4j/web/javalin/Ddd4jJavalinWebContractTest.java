package io.ddd4j.web.javalin;

import io.ddd4j.cache.CacheKit;
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
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jJavalinWebContractTest extends AbstractWebContractTest {

    private Javalin app;
    private WebContractClient contractClient;

    @BeforeEach
    void setUp() {
        CacheKit.build("javalin-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
        Ddd4jJavalinWeb ddd4jWeb = new Ddd4jJavalinWeb(new WebRequestContextFactory(), requestLifecycle,
                new DefaultWebExceptionTranslator(),
                new WebIdempotencyLifecycle(new CacheIdempotencyGuard("javalin-contract")));
        app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson());
            ddd4jWeb.configure(config);
            registerContractRoutes(config.routes);
        }).start(0);
        contractClient = new JavalinContractClient(HttpClient.newHttpClient(), app.port());
    }

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("javalin-contract");
    }

    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    private void registerContractRoutes(io.javalin.config.RoutesConfig routes) {
        routes.get(WebContractPaths.SUCCESS, context -> context.json(R.ok(Map.of("result", "ok"))));
        routes.get(WebContractPaths.PUBLIC, context -> context.json(R.ok(Map.of("result", "ok"))));
        routes.get(WebContractPaths.PROTECTED, context -> context.json(R.ok(Map.of("result", "ok"))));
        routes.post(WebContractPaths.CREATED, context -> context.status(201)
                .json(R.ok(Map.of("result", "created"))));
        routes.get(WebContractPaths.CONTEXT, context -> {
            Map<String, Object> requestContext = new LinkedHashMap<>();
            requestContext.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            requestContext.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            requestContext.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            context.json(R.ok(requestContext));
        });
        routes.post(WebContractPaths.IDEMPOTENT,
                context -> context.json(R.ok(Map.of("result", "accepted"))));
        routes.get("/contract/errors/{type}", context -> {
            String type = context.pathParam("type");
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
        });
    }

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    private record JavalinContractClient(HttpClient httpClient, int port) implements WebContractClient {

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path));
                headers.forEach(builder::header);
                HttpRequest.BodyPublisher publisher = Objects.isNull(body)
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
                HttpResponse<String> response = httpClient.send(builder.method(method, publisher).build(),
                        HttpResponse.BodyHandlers.ofString());
                return new WebContractResponse(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Javalin contract request interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("Javalin contract request failed", exception);
            }
        }
    }
}
