package io.ddd4j.web.quarkus;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractResponse;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled("Quarkus RestEasy Jackson 与 Jackson 3.x 不兼容（VerifyError: StreamConstraintsException）")
@QuarkusTest
class Ddd4jQuarkusWebContractTest extends AbstractWebContractTest {

    @TestHTTPResource("/")
    URI baseUri;

    private final WebContractClient contractClient = new QuarkusContractClient();

    @BeforeEach
    void setUp() {
        CacheKit.build("quarkus-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("quarkus-contract");
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

    private final class QuarkusContractClient implements WebContractClient {

        private final HttpClient httpClient = HttpClient.newHttpClient();

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path));
            headers.forEach(builder::header);
            HttpRequest.BodyPublisher publisher = Objects.isNull(body)
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            if (Objects.nonNull(body)) {
                builder.header("Content-Type", "application/json");
            }
            try {
                HttpResponse<String> response = httpClient.send(builder.method(method, publisher).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                Map<String, List<String>> responseHeaders = response.headers().map();
                return new WebContractResponse(response.statusCode(), responseHeaders, response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Quarkus contract request was interrupted", exception);
            } catch (IOException exception) {
                throw new IllegalStateException("Quarkus contract request failed", exception);
            }
        }
    }
}
