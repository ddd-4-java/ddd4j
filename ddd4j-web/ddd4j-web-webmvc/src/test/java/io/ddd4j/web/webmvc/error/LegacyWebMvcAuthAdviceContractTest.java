package io.ddd4j.web.webmvc.error;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.PermissionDeniedException;
import io.ddd4j.core.exception.TokenExpiredException;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class LegacyWebMvcAuthAdviceContractTest {

    @Test
    void globalRestAuthenticationUsesUnauthorizedStatus() throws Exception {
        assertAuthentication(mockMvc(new GlobalRestExceptionAdvice()));
    }

    @Test
    void globalRestAuthorizationUsesForbiddenStatus() throws Exception {
        assertAuthorization(mockMvc(new GlobalRestExceptionAdvice()));
    }

    @Test
    void globalHandlerAuthenticationUsesUnauthorizedStatus() throws Exception {
        assertAuthentication(mockMvc(globalHandler()));
    }

    @Test
    void globalHandlerAuthorizationUsesForbiddenStatus() throws Exception {
        assertAuthorization(mockMvc(globalHandler()));
    }

    @Test
    void globalRestKeepsLegacyBusinessFailureResponse() throws Exception {
        assertResponse(mockMvc(new GlobalRestExceptionAdvice()), "/business", "",
                200, ApiCode.FAIL.getCode(), "Invalid order");
    }

    @Test
    void globalHandlerKeepsLegacyBusinessFailureResponse() throws Exception {
        assertResponse(mockMvc(globalHandler()), "/business", "", 200, 422, "Invalid order");
    }

    private GlobalExceptionHandler globalHandler() {
        return new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties());
    }

    private MockMvc mockMvc(Object advice) {
        return MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build();
    }

    private void assertAuthentication(MockMvc mvc) throws Exception {
        assertResponse(mvc, "/authentication", "Token expired", 401, 401, "Token expired");
        assertResponse(mvc, "/authentication", "", 401, 401, ApiCode.UNAUTHORIZED.getReason());
    }

    private void assertAuthorization(MockMvc mvc) throws Exception {
        assertResponse(mvc, "/authorization", "Access refused", 403, 403, "Access refused");
        assertResponse(mvc, "/authorization", "  ", 403, 403, ApiCode.FORBIDDEN.getReason());
    }

    private void assertResponse(MockMvc mvc, String path, String suppliedMessage, int status, int code,
                                String expectedMessage) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path).param("message", suppliedMessage))
                .andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(status, response.getStatus());
        assertEquals(code, body.get("code"));
        assertEquals(expectedMessage, body.get("msg"));
    }

    @RestController
    static class Endpoint {
        @GetMapping("/authentication")
        void authenticate(@RequestParam("message") String message) {
            throw new TokenExpiredException(message);
        }

        @GetMapping("/authorization")
        void authorize(@RequestParam("message") String message) {
            throw new PermissionDeniedException(message);
        }

        @GetMapping("/business")
        void failBusiness() {
            throw new BizRuntimeException(422, "Invalid order");
        }
    }
}
