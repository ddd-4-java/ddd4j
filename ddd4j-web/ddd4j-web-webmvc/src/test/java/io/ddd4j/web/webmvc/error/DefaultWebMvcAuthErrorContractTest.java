package io.ddd4j.web.webmvc.error;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.core.exception.PermissionDeniedException;
import io.ddd4j.core.exception.TokenExpiredException;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.webmvc.Ddd4jWebMvcExceptionHandler;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class DefaultWebMvcAuthErrorContractTest {

    private static final Map<String, String> CUSTOM_DETAILS = Collections.singletonMap("source", "custom-translator");

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
            .build();

    @Test
    void expiredTokenUsesUnauthorizedHttpStatusAndKeepsMessage() throws Exception {
        assertAuthError("/authentication", ApiCode.UNAUTHORIZED, "Token expired");
    }

    @Test
    void missingPermissionUsesForbiddenHttpStatusAndDefaultReason() throws Exception {
        assertAuthError("/authorization", ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    @Test
    void mvcDispatchUsesTheInjectedTranslatorForBothAuthFailures() throws Exception {
        MockMvc customized = MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(DefaultWebMvcAuthErrorContractTest::customError))
                .build();

        assertCustomAuthError(customized, "/authentication", "TokenExpiredException", CUSTOM_DETAILS);
        assertCustomAuthError(customized, "/authorization", "PermissionDeniedException", CUSTOM_DETAILS);
    }

    @Test
    void webApplicationContextUsesCustomTranslatorWithBothLegacyAdvices() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(CombinedAdviceConfiguration.class);
            context.refresh();

            MockMvc customized = MockMvcBuilders.webAppContextSetup(context).build();
            assertCustomAuthError(customized, "/authentication", "TokenExpiredException", CUSTOM_DETAILS);
            assertCustomAuthError(customized, "/authorization", "PermissionDeniedException", CUSTOM_DETAILS);
        }
    }

    private static WebError customError(Throwable failure) {
        return new WebError(409, "custom-auth", failure.getClass().getSimpleName(), CUSTOM_DETAILS);
    }

    @Test
    void directAuthResponseMethodsKeepDefaultResponseContract() {
        Ddd4jWebMvcExceptionHandler handler = new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator());
        assertDirectAuthError(handler.authenticationFailed(new TokenExpiredException("Token expired")),
                ApiCode.UNAUTHORIZED, "Token expired");
        assertDirectAuthError(handler.authorizationFailed(new PermissionDeniedException("  ")),
                ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    private void assertDirectAuthError(ResponseEntity<R<String>> response, ApiCode expected, String message) {
        R<String> body = response.getBody();
        assertEquals(expected.getCode().intValue(), response.getStatusCode().value());
        assertEquals(expected.getCode(), body.getCode());
        assertEquals(message, body.getMsg());
    }

    private void assertCustomAuthError(MockMvc customized, String path, String message,
                                       Map<String, String> details) throws Exception {
        MockHttpServletResponse response = customized.perform(get(path)).andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(409, response.getStatus());
        assertEquals("custom-auth", body.get("code"));
        assertEquals(message, body.get("msg"));
        assertEquals(details, body.get("data"));
    }

    private void assertAuthError(String path, ApiCode expected, String message) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(expected.getCode().intValue(), response.getStatus());
        assertEquals(expected.getCode(), body.get("code"));
        assertEquals(message, body.get("msg"));
    }

    @Configuration
    @EnableWebMvc
    @Import({Endpoint.class, Ddd4jWebMvcExceptionHandler.class, GlobalRestExceptionAdvice.class, GlobalExceptionHandler.class})
    static class CombinedAdviceConfiguration {

        @Bean
        WebExceptionTranslator exceptionTranslator() {
            return DefaultWebMvcAuthErrorContractTest::customError;
        }

        @Bean
        NestedMessageSource nestedMessageSource() {
            return new NestedMessageSource();
        }

        @Bean
        ServerI18nProperties serverI18nProperties() {
            return new ServerI18nProperties();
        }
    }

    @RestController
    static class Endpoint {

        @GetMapping("/authentication")
        void authenticate() {
            throw new TokenExpiredException("Token expired");
        }

        @GetMapping("/authorization")
        void authorize() {
            throw new PermissionDeniedException("  ");
        }
    }
}
