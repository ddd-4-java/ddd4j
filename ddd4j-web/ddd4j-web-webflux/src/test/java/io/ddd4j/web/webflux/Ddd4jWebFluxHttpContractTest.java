package io.ddd4j.web.webflux;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.CacheIdempotencyGuard;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebAccessPolicy;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.ddd4j.web.core.WebStatusException;
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
            return R.ok(Map.of("result", "ok"));
        }

        @PostMapping(WebContractPaths.CREATED)
        ResponseEntity<R<Map<String, String>>> created() {
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(Map.of("result", "created")));
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
            return R.ok(Map.of("result", "accepted"));
        }

        @GetMapping("/contract/errors/{type}")
        Mono<R<Void>> error(@PathVariable("type") String type) {
            Throwable throwable = switch (type) {
                case "bad-request" -> new IllegalArgumentException("bad request");
                case "forbidden" -> new SecurityException("forbidden");
                case "not-found" -> new NoSuchElementException("not found");
                case "conflict" -> new IllegalStateException("conflict");
                case "unsupported-media-type" -> new WebStatusException(415, "unsupported media type");
                case "unprocessable-entity" -> new WebStatusException(422, "unprocessable entity");
                case "too-many-requests" -> new WebStatusException(429, "too many requests");
                default -> new RuntimeException("internal failure");
            };
            return Mono.error(throwable);
        }
    }

    private record WebFluxContractClient(WebTestClient webTestClient) implements WebContractClient {

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
