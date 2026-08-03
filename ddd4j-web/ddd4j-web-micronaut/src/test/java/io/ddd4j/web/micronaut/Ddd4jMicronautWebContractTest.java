package io.ddd4j.web.micronaut;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebStatusException;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

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

        Map<String, Object> properties = Map.of(
                "micronaut.server.port", -1,
                "ddd4j.web.public-paths", List.of(
                        WebContractPaths.SUCCESS,
                        WebContractPaths.CREATED,
                        WebContractPaths.PUBLIC,
                        WebContractPaths.CONTEXT,
                        WebContractPaths.IDEMPOTENT,
                        "/contract/errors/**"),
                "ddd4j.web.idempotency-cache-name", "micronaut-contract");
        server = ApplicationContext.run(EmbeddedServer.class, properties);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        contractClient = new MicronautContractClient(HttpClient.newHttpClient(), server.getPort());
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

    private record MicronautContractClient(HttpClient httpClient, int port) implements WebContractClient {

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path));
                headers.forEach(builder::header);
                HttpRequest.BodyPublisher publisher = Objects.isNull(body)
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
                java.net.http.HttpResponse<String> response = httpClient.send(
                        builder.method(method, publisher).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                return new WebContractResponse(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Micronaut contract request interrupted", exception);
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
        return R.ok(Map.of("result", "ok"));
    }

    @Get("/public")
    R<Map<String, String>> publicEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    @Get("/protected")
    R<Map<String, String>> protectedEndpoint() {
        return R.ok(Map.of("result", "ok"));
    }

    @Post("/created")
    HttpResponse<R<Map<String, String>>> created() {
        return HttpResponse.created(R.ok(Map.of("result", "created")));
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
        return R.ok(Map.of("result", "accepted"));
    }

    @Get("/errors/{type}")
    R<Void> error(String type) {
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
