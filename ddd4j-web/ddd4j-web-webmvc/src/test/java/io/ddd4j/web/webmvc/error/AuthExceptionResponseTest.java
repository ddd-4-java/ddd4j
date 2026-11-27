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

/** \u9a8c\u8bc1 <code>/authentication</code> \u7b49\u8def\u7531\u7ecf\u793a\u4f8b HTTP \u6846\u67b6\u6267\u884c\u540e\u7684\u8bf7\u6c42\u884c\u4e3a\u3002 */
class AuthExceptionResponseTest {

    /**
     * \u6784\u9020 <code>GlobalExceptionHandler</code>\u3001<code>NestedMessageSource</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Stream&lt;Object&gt;</code> \u7ed3\u679c
     */
    static Stream<Object> advice() {
        return Stream.of(new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties()),
                new GlobalRestExceptionAdvice(),
                new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()));
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>MockMvcBuilders.standaloneSetup()</code>\u3001<code>ApiCode.UNAUTHORIZED.getReason()</code>\u3001<code>ApiCode.FORBIDDEN.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @param advice \u4f20\u7ed9 <code>MockMvcBuilders.standaloneSetup()</code> \u7684 <code>Object</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @ParameterizedTest
    @MethodSource("advice")
    void mapsAuthFailuresWithCustomAndDefaultMessages(Object advice) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build();
        assertResponse(mvc, "/authentication", "令牌已于昨天失效", ApiCode.UNAUTHORIZED, "令牌已于昨天失效");
        assertResponse(mvc, "/authentication", null, ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
        assertResponse(mvc, "/authorization", "无权查看这笔订单", ApiCode.FORBIDDEN, "无权查看这笔订单");
        assertResponse(mvc, "/authorization", "  ", ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u6838\u5bf9<code>response.getStatus()</code> \u7b49\u4e8e <code>code.getCode().intValue()</code>\uff0c<code>body.required("code").asInt()</code> \u7b49\u4e8e <code>code.getCode().intValue()</code>\u3002
     * @param mvc \u4f20\u7ed9 <code>mvc.perform()</code> \u7684 <code>MockMvc</code> \u8f93\u5165
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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

    /** \u4e3a HTTP \u5408\u540c\u63d0\u4f9b\u5b9e\u9645\u8bf7\u6c42\u8def\u7531\u7684 Spring \u6d4b\u8bd5\u63a7\u5236\u5668\u3002 */
    @RestController
    static class Endpoint {
        /**
         * \u6784\u9020 <code>TokenExpiredException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
         */
        @GetMapping("/authentication")
        void authenticate(@RequestParam(name = "message", required = false) String message) {
            throw new TokenExpiredException(message);
        }

        /**
         * \u6784\u9020 <code>PermissionDeniedException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
         */
        @GetMapping("/authorization")
        void authorize(@RequestParam(name = "message", required = false) String message) {
            throw new PermissionDeniedException(message);
        }
    }
}
