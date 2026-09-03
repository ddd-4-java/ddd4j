package io.ddd4j.web.micronaut;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jMicronautWebContractTest extends AbstractWebContractTest {

    private EmbeddedServer server;
    private WebContractClient contractClient;

    @BeforeEach
    void setUp() {
        CacheKit.build("micronaut-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("micronaut.server.port", -1);
        properties.put("ddd4j.web.public-paths", Arrays.asList(
                WebContractPaths.SUCCESS,
                WebContractPaths.CREATED,
                WebContractPaths.PUBLIC,
                WebContractPaths.CONTEXT,
                WebContractPaths.IDEMPOTENT,
                "/contract/errors/**"));
        properties.put("ddd4j.web.idempotency-cache-name", "micronaut-contract");
        server = ApplicationContext.run(EmbeddedServer.class, properties);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        contractClient = new MicronautContractClient(server.getPort());
    }

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.close();
        }
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("micronaut-contract");
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

    private static final class MicronautContractClient implements WebContractClient {
        private final int port;

        MicronautContractClient(int port) {
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
                throw new IllegalStateException("Micronaut contract request failed", exception);
            }
        }
    }
}

@Controller("/contract")
final class MicronautContractController {

    @Get("/success")
    R<Map<String, String>> success() {
        return R.ok(Collections.singletonMap("result", "ok"));
    }

    @Get("/public")
    R<Map<String, String>> publicEndpoint() {
        return R.ok(Collections.singletonMap("result", "ok"));
    }

    @Get("/protected")
    R<Map<String, String>> protectedEndpoint() {
        return R.ok(Collections.singletonMap("result", "ok"));
    }

    @Post("/created")
    HttpResponse<R<Map<String, String>>> created() {
        return HttpResponse.created(R.ok(Collections.singletonMap("result", "created")));
    }

    @Get("/context")
    R<Map<String, Object>> context() {
        WebRequestContext requestContext = Ddd4jMicronautContext.current()
                .orElseThrow(() -> new IllegalStateException("Micronaut request context is missing"))
                .requestContext();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestContext.requestId());
        data.put("traceId", requestContext.traceId());
        data.put("tenantId", requestContext.tenantId());
        return R.ok(data);
    }

    @Post("/idempotent")
    R<Map<String, String>> idempotent() {
        return R.ok(Collections.singletonMap("result", "accepted"));
    }

    @Get("/errors/{type}")
    R<Void> error(String type) {
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
    }
}
