package io.ddd4j.web.webflux;

import java.util.Collections;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.ddd4j.web.webflux.error.GlobalErrorAttributes;
import io.ddd4j.web.webflux.error.GlobalErrorWebExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jWebFluxHttpContractTest extends AbstractWebContractTest {

    private AnnotationConfigApplicationContext applicationContext;
    private WebContractClient contractClient;

    @BeforeEach
    void setUp() {
        CacheKit.build("webflux-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        applicationContext = new AnnotationConfigApplicationContext(ContractConfiguration.class);
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
        contractClient = new WebFluxContractClient(webTestClient);
    }

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(applicationContext)) {
            applicationContext.close();
        }
        BaseContext.clear();
        CacheKit.unregister("webflux-contract");
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

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class ContractConfiguration {

        @Bean
        ContractController contractController() {
            return new ContractController();
        }

        @Bean
        Ddd4jWebFluxFilter ddd4jWebFluxFilter() {
            BearerSubjectAuthenticator authenticator = new BearerSubjectAuthenticator();
            WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(authenticator,
                    WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
            return new Ddd4jWebFluxFilter(new WebRequestContextFactory(), requestLifecycle,
                    new WebIdempotencyLifecycle(new CacheIdempotencyGuard("webflux-contract")));
        }

        @Bean
        GlobalErrorWebExceptionHandler globalErrorWebExceptionHandler() {
            return new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),
                    new DefaultWebExceptionTranslator());
        }
    }

    @RestController
    static class ContractController {

        @GetMapping({WebContractPaths.SUCCESS, WebContractPaths.PUBLIC, WebContractPaths.PROTECTED})
        R<Map<String, String>> success() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @PostMapping(WebContractPaths.CREATED)
        ResponseEntity<R<Map<String, String>>> created() {
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Collections.singletonMap("result", "created")));
        }

        @GetMapping(WebContractPaths.CONTEXT)
        Mono<R<Map<String, Object>>> context() {
            return Ddd4jWebFluxContext.currentRequest().map(requestContext -> {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("requestId", requestContext.requestId());
                context.put("traceId", requestContext.traceId());
                context.put("tenantId", requestContext.tenantId());
                return R.ok(context);
            });
        }

        @PostMapping(WebContractPaths.IDEMPOTENT)
        R<Map<String, String>> idempotent() {
            return R.ok(Collections.singletonMap("result", "accepted"));
        }

        @GetMapping("/contract/errors/{type}")
        Mono<R<Void>> error(@PathVariable("type") String type) {
            Throwable throwable;
            switch (type) {
                case "bad-request": throwable = new IllegalArgumentException("bad request"); break;
                case "forbidden": throwable = new SecurityException("forbidden"); break;
                case "not-found": throwable = new NoSuchElementException("not found"); break;
                case "conflict": throwable = new IllegalStateException("conflict"); break;
                case "unsupported-media-type": throwable = new WebStatusException(415, "unsupported media type"); break;
                case "unprocessable-entity": throwable = new WebStatusException(422, "unprocessable entity"); break;
                case "too-many-requests": throwable = new WebStatusException(429, "too many requests"); break;
                default: throwable = new RuntimeException("internal failure"); break;
            }
            return Mono.error(throwable);
        }
    }

    private static final class WebFluxContractClient implements WebContractClient {
        private final WebTestClient webTestClient;

        public WebFluxContractClient(WebTestClient webTestClient) {
            this.webTestClient = webTestClient;
        }
        public WebTestClient webTestClient() { return webTestClient; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WebFluxContractClient)) return false;
            WebFluxContractClient other = (WebFluxContractClient) o;
            return Objects.equals(this.webTestClient, other.webTestClient);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(webTestClient);
        }
        @Override
        public String toString() {
            return "WebFluxContractClient{" + "webTestClient=" + webTestClient + "}";
        }
        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            WebTestClient.RequestBodySpec request = webTestClient.method(HttpMethod.valueOf(method)).uri(path);
            headers.forEach(request::header);
            if (StringUtils.hasLength(body)) {
                request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            }
            EntityExchangeResult<byte[]> result = request.exchange().expectBody().returnResult();
            Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
            result.getResponseHeaders().forEach(responseHeaders::put);
            byte[] responseBody = result.getResponseBody();
            String responseText = Objects.isNull(responseBody)
                    ? "" : new String(responseBody, StandardCharsets.UTF_8);
            return new WebContractResponse(result.getStatus().value(), responseHeaders, responseText);
        }
    
    }
}
