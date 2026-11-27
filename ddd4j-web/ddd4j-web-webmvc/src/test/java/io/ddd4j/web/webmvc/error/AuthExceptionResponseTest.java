package io.ddd4j.web.webmvc.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.exception.PermissionDeniedException;
import io.ddd4j.core.exception.TokenExpiredException;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.webmvc.Ddd4jWebMvcExceptionHandler;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AuthExceptionResponseTest {

    static Stream<Object> advice() {
        return Stream.of(new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties()),
                new GlobalRestExceptionAdvice(),
                new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()));
    }

    @ParameterizedTest
    @MethodSource("advice")
    void mapsAuthFailuresWithCustomAndDefaultMessages(Object advice) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build();
        assertResponse(mvc, "/authentication", "令牌已于昨天失效", ApiCode.UNAUTHORIZED, "令牌已于昨天失效");
        assertResponse(mvc, "/authentication", null, ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
        assertResponse(mvc, "/authorization", "无权查看这笔订单", ApiCode.FORBIDDEN, "无权查看这笔订单");
        assertResponse(mvc, "/authorization", "  ", ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    private void assertResponse(MockMvc mvc, String path, String message, ApiCode code, String expected) throws Exception {
        var request = get(path);
        if (Objects.nonNull(message)) {
            request.param("message", message);
        }
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertEquals(code.getCode().intValue(), response.getStatus());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(code.getCode().intValue(), body.required("code").asInt());
        assertEquals(expected, body.required("msg").asText());
    }

    @RestController
    static class Endpoint {
        @GetMapping("/authentication")
        void authenticate(@RequestParam(name = "message", required = false) String message) {
            throw new TokenExpiredException(message);
        }

        @GetMapping("/authorization")
        void authorize(@RequestParam(name = "message", required = false) String message) {
            throw new PermissionDeniedException(message);
        }
    }
}
