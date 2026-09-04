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
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

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
        app = Javalin.create(config -> config.showJavalinBanner = false);
        ddd4jWeb.configure(app);
        registerContractRoutes(app);
        app.start(0);
        contractClient = new JavalinContractClient(app.port());
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

    private void registerContractRoutes(Javalin javalinApp) {
        javalinApp.get(WebContractPaths.SUCCESS, context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        javalinApp.get(WebContractPaths.PUBLIC, context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        javalinApp.get(WebContractPaths.PROTECTED, context -> context.json(R.ok(Collections.singletonMap("result", "ok"))));
        javalinApp.post(WebContractPaths.CREATED, context -> context.status(201)
                .json(R.ok(Collections.singletonMap("result", "created")));
        javalinApp.get(WebContractPaths.CONTEXT, context -> {
            Map<String, Object> requestContext = new LinkedHashMap<>();
            requestContext.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            requestContext.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            requestContext.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            context.json(R.ok(requestContext));
        });
        javalinApp.post(WebContractPaths.IDEMPOTENT,
                context -> context.json(R.ok(Collections.singletonMap("result", "accepted")));
        javalinApp.get("/contract/errors/{type}", context -> {
            String type = context.pathParam("type");
            Exception ex;
            switch (type) {
                case "bad-request": ex = new IllegalArgumentException("bad request"); break;
                case "forbidden": ex = new SecurityException("forbidden"); break;
                case "not-found": ex = new NoSuchElementException("not found"); break;
                case "conflict": ex = new IllegalStateException("conflict"); break;
                case "unsupported-media-type": ex = new WebStatusException(415, "unsupported media type"); break;
                case "unprocessable-entity": ex = new WebStatusException(422, "unprocessable entity"); break;
                case "too-many-requests": ex = new WebStatusException(429, "too many requests"); break;
                default: ex = new RuntimeException("internal failure"); break;
            }
            throw ex;
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

    private static final class JavalinContractClient implements WebContractClient {
        private final int port;

        JavalinContractClient(int port) {
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
                throw new IllegalStateException("Javalin contract request failed", exception);
            }
        }
    }
}
