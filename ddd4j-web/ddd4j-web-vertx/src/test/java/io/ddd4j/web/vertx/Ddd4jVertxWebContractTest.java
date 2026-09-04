package io.ddd4j.web.vertx;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled("Vert.x 4.x contract test — requires running Vert.x instance")
class Ddd4jVertxWebContractTest extends AbstractWebContractTest {

    private Vertx vertx;
    private HttpServer server;
    private WebContractClient contractClient;

    @BeforeEach
    void setUp() {
        CacheKit.build("vertx-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
        new Ddd4jVertxWeb(new WebRequestContextFactory(), requestLifecycle,
                new DefaultWebExceptionTranslator(),
                new WebIdempotencyLifecycle(new CacheIdempotencyGuard("vertx-contract")),
                io.vertx.core.json.Json::encode).install(router);
        registerContractRoutes(router);
        server = vertx.createHttpServer().requestHandler(router).listen(0)
                .toCompletionStage().toCompletableFuture().join();
        contractClient = new VertxContractClient(server.actualPort());
    }

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.close().toCompletionStage().toCompletableFuture().join();
        }
        if (Objects.nonNull(vertx)) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
        BaseContext.clear();
        CacheKit.unregister("vertx-contract");
    }

    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    private void registerContractRoutes(Router router) {
        router.get(WebContractPaths.SUCCESS).handler(context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        router.get(WebContractPaths.PUBLIC).handler(context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        router.get(WebContractPaths.PROTECTED).handler(context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        router.post(WebContractPaths.CREATED).handler(context -> {
            context.response().setStatusCode(201);
            context.json(R.ok(Collections.singletonMap("result", "created")));
        });
        router.get(WebContractPaths.CONTEXT).handler(context -> {
            Map<String, Object> requestContext = new LinkedHashMap<>();
            Ddd4jVertxContext.request(context).ifPresent(value -> {
                requestContext.put("requestId", value.requestId());
                requestContext.put("traceId", value.traceId());
                requestContext.put("tenantId", value.tenantId());
            });
            context.json(R.ok(requestContext));
        });
        router.post(WebContractPaths.IDEMPOTENT)
                .handler(context -> context.json(R.ok(Collections.singletonMap("result", "accepted"))));
        router.get("/contract/errors/:type").handler(context -> context.fail(error(context.pathParam("type"))));
    }

    private Throwable error(String type) {
        switch (type) {
            case "bad-request": return new IllegalArgumentException("bad request");
            case "forbidden": return new SecurityException("forbidden");
            case "not-found": return new NoSuchElementException("not found");
            case "conflict": return new IllegalStateException("conflict");
            case "unsupported-media-type": return new WebStatusException(415, "unsupported media type");
            case "unprocessable-entity": return new WebStatusException(422, "unprocessable entity");
            case "too-many-requests": return new WebStatusException(429, "too many requests");
            default: return new RuntimeException("internal failure");
        }
    }

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    private static final class VertxContractClient implements WebContractClient {
        private final int port;

        VertxContractClient(int port) {
            this.port = port;
        }

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            try {
                URL url = new URL("http://127.0.0.1:" + port + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
                if (body != null) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int statusCode = conn.getResponseCode();
                Map<String, java.util.List<String>> responseHeaders = conn.getHeaderFields();
                String responseBody;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    responseBody = reader.lines().collect(Collectors.joining("\n"));
                } catch (Exception e) {
                    responseBody = "";
                }
                conn.disconnect();
                return new WebContractResponse(statusCode, responseHeaders, responseBody);
            } catch (Exception exception) {
                throw new IllegalStateException("Vert.x contract request failed", exception);
            }
        }
    }
}
