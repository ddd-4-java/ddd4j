package io.ddd4j.web.webmvc;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jWebMvcContractTest extends AbstractWebContractTest {

    private WebContractClient contractClient;

    @BeforeEach
    void setUp() {
        CacheKit.build("webmvc-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));

        BearerSubjectAuthenticator authenticator = new BearerSubjectAuthenticator();
        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(path -> !WebContractPaths.PROTECTED.equals(path)));
        Ddd4jWebMvcInterceptor interceptor = new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                requestLifecycle, new WebIdempotencyLifecycle(new CacheIdempotencyGuard("webmvc-contract")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
                .build();
        contractClient = new MockMvcContractClient(mockMvc);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("webmvc-contract");
    }

    @Override
    protected WebContractClient client() {
        return contractClient;
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
        R<Map<String, Object>> context() {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            context.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            context.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            return R.ok(context);
        }

        @PostMapping(WebContractPaths.IDEMPOTENT)
        R<Map<String, String>> idempotent() {
            return R.ok(Map.of("result", "accepted"));
        }

        @GetMapping("/contract/errors/{type}")
        R<Void> error(@PathVariable("type") String type) {
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

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    private record MockMvcContractClient(MockMvc mockMvc) implements WebContractClient {

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            try {
                MockHttpServletResponse response = mockMvc.perform(requestBuilder(method, path, headers, body))
                        .andReturn().getResponse();
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                response.getHeaderNames().forEach(name -> responseHeaders.put(name, response.getHeaders(name)));
                return new WebContractResponse(response.getStatus(), responseHeaders,
                        response.getContentAsString());
            } catch (Exception exception) {
                throw new IllegalStateException("WebMVC contract request failed", exception);
            }
        }

        private MockHttpServletRequestBuilder requestBuilder(String method, String path,
                                                             Map<String, String> headers, String body) {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
            headers.forEach(builder::header);
            if (StringUtils.hasLength(body)) {
                builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return builder;
        }
    }
}
