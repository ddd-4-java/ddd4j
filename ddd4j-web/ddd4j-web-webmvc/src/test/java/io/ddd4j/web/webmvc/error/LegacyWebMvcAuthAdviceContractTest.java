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

/** \u9a8c\u8bc1 <code>/authentication</code> \u7b49\u8def\u7531\u7ecf\u793a\u4f8b HTTP \u6846\u67b6\u6267\u884c\u540e\u7684\u8bf7\u6c42\u884c\u4e3a\u3002 */
class LegacyWebMvcAuthAdviceContractTest {

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mockMvc()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void globalRestAuthenticationUsesUnauthorizedStatus() throws Exception {
        assertAuthentication(mockMvc(new GlobalRestExceptionAdvice()));
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mockMvc()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void globalRestAuthorizationUsesForbiddenStatus() throws Exception {
        assertAuthorization(mockMvc(new GlobalRestExceptionAdvice()));
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mockMvc()</code>\u3001<code>globalHandler()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void globalHandlerAuthenticationUsesUnauthorizedStatus() throws Exception {
        assertAuthentication(mockMvc(globalHandler()));
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mockMvc()</code>\u3001<code>globalHandler()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void globalHandlerAuthorizationUsesForbiddenStatus() throws Exception {
        assertAuthorization(mockMvc(globalHandler()));
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mockMvc()</code>\u3001<code>ApiCode.FAIL.getCode()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void globalRestKeepsLegacyBusinessFailureResponse() throws Exception {
        assertResponse(mockMvc(new GlobalRestExceptionAdvice()), "/business", "",
                200, ApiCode.FAIL.getCode(), "Invalid order");
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mockMvc()</code>\u3001<code>globalHandler()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void globalHandlerKeepsLegacyBusinessFailureResponse() throws Exception {
        assertResponse(mockMvc(globalHandler()), "/business", "", 200, 422, "Invalid order");
    }

    /**
     * \u521b\u5efa <code>new GlobalExceptionHandler</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties())</code>
     */
    private GlobalExceptionHandler globalHandler() {
        return new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties());
    }

    /**
     * \u901a\u8fc7 <code>MockMvcBuilders.standaloneSetup()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param advice \u4f20\u7ed9 <code>MockMvcBuilders.standaloneSetup()</code> \u7684 <code>Object</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build()</code>
     */
    private MockMvc mockMvc(Object advice) {
        return MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build();
    }

    /**
     * \u6838\u5bf9<code>ApiCode.UNAUTHORIZED.getReason()</code> \u8fd4\u56de\u7684\u6d4b\u8bd5\u7ed3\u679c\u3002
     * @param mvc \u4f20\u7ed9 <code>assertResponse()</code> \u7684 <code>MockMvc</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private void assertAuthentication(MockMvc mvc) throws Exception {
        assertResponse(mvc, "/authentication", "Token expired", 401, 401, "Token expired");
        assertResponse(mvc, "/authentication", "", 401, 401, ApiCode.UNAUTHORIZED.getReason());
    }

    /**
     * \u6838\u5bf9<code>ApiCode.FORBIDDEN.getReason()</code> \u8fd4\u56de\u7684\u6d4b\u8bd5\u7ed3\u679c\u3002
     * @param mvc \u4f20\u7ed9 <code>assertResponse()</code> \u7684 <code>MockMvc</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private void assertAuthorization(MockMvc mvc) throws Exception {
        assertResponse(mvc, "/authorization", "Access refused", 403, 403, "Access refused");
        assertResponse(mvc, "/authorization", "  ", 403, 403, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u6838\u5bf9<code>response.getStatus()</code> \u7b49\u4e8e <code>status</code>\uff0c<code>body.get("code")</code> \u7b49\u4e8e <code>code</code>\u3002
     * @param mvc \u4f20\u7ed9 <code>mvc.perform()</code> \u7684 <code>MockMvc</code> \u8f93\u5165
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param suppliedMessage \u4f20\u7ed9 <code>mvc.perform()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param expectedMessage \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private void assertResponse(MockMvc mvc, String path, String suppliedMessage, int status, int code,
                                String expectedMessage) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path).param("message", suppliedMessage))
                .andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(status, response.getStatus());
        assertEquals(code, body.get("code"));
        assertEquals(expectedMessage, body.get("msg"));
    }

    /** \u4e3a HTTP \u5408\u540c\u63d0\u4f9b\u5b9e\u9645\u8bf7\u6c42\u8def\u7531\u7684 Spring \u6d4b\u8bd5\u63a7\u5236\u5668\u3002 */
    @RestController
    static class Endpoint {
        /**
         * \u6784\u9020 <code>TokenExpiredException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
         */
        @GetMapping("/authentication")
        void authenticate(@RequestParam("message") String message) {
            throw new TokenExpiredException(message);
        }

        /**
         * \u6784\u9020 <code>PermissionDeniedException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
         */
        @GetMapping("/authorization")
        void authorize(@RequestParam("message") String message) {
            throw new PermissionDeniedException(message);
        }

        /** \u6784\u9020 <code>BizRuntimeException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002 */
        @GetMapping("/business")
        void failBusiness() {
            throw new BizRuntimeException(422, "Invalid order");
        }
    }
}
