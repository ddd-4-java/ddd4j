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

/**
 * 验证 <code>/authentication</code> 等路由经示例 HTTP 框架执行后的请求行为。
 */
class LegacyWebMvcAuthAdviceContractTest {

    /**
     * 在测试框架中执行 <code>mockMvc()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void globalRestAuthenticationUsesUnauthorizedStatus() throws Exception {
        assertAuthentication(mockMvc(new GlobalRestExceptionAdvice()));
    }

    /**
     * 在测试框架中执行 <code>mockMvc()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void globalRestAuthorizationUsesForbiddenStatus() throws Exception {
        assertAuthorization(mockMvc(new GlobalRestExceptionAdvice()));
    }

    /**
     * 在测试框架中执行 <code>mockMvc()</code>、<code>globalHandler()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void globalHandlerAuthenticationUsesUnauthorizedStatus() throws Exception {
        assertAuthentication(mockMvc(globalHandler()));
    }

    /**
     * 在测试框架中执行 <code>mockMvc()</code>、<code>globalHandler()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void globalHandlerAuthorizationUsesForbiddenStatus() throws Exception {
        assertAuthorization(mockMvc(globalHandler()));
    }

    /**
     * 在测试框架中执行 <code>mockMvc()</code>、<code>ApiCode.FAIL.getCode()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void globalRestKeepsLegacyBusinessFailureResponse() throws Exception {
        assertResponse(mockMvc(new GlobalRestExceptionAdvice()), "/business", "",
                200, ApiCode.FAIL.getCode(), "Invalid order");
    }

    /**
     * 在测试框架中执行 <code>mockMvc()</code>、<code>globalHandler()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void globalHandlerKeepsLegacyBusinessFailureResponse() throws Exception {
        assertResponse(mockMvc(globalHandler()), "/business", "", 200, 422, "Invalid order");
    }

    /**
     * 创建 <code>new GlobalExceptionHandler</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties())</code>
     */
    private GlobalExceptionHandler globalHandler() {
        return new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties());
    }

    /**
     * 通过 <code>MockMvcBuilders.standaloneSetup()</code> 取得当前测试需要的结果。
     *
     * @param advice 传给 <code>MockMvcBuilders.standaloneSetup()</code> 的 <code>Object</code> 输入
     * @return 当前测试使用的结果，取自 <code>MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build()</code>
     */
    private MockMvc mockMvc(Object advice) {
        return MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build();
    }

    /**
     * 核对<code>ApiCode.UNAUTHORIZED.getReason()</code> 返回的测试结果。
     *
     * @param mvc 传给 <code>assertResponse()</code> 的 <code>MockMvc</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private void assertAuthentication(MockMvc mvc) throws Exception {
        assertResponse(mvc, "/authentication", "Token expired", 401, 401, "Token expired");
        assertResponse(mvc, "/authentication", "", 401, 401, ApiCode.UNAUTHORIZED.getReason());
    }

    /**
     * 核对<code>ApiCode.FORBIDDEN.getReason()</code> 返回的测试结果。
     *
     * @param mvc 传给 <code>assertResponse()</code> 的 <code>MockMvc</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private void assertAuthorization(MockMvc mvc) throws Exception {
        assertResponse(mvc, "/authorization", "Access refused", 403, 403, "Access refused");
        assertResponse(mvc, "/authorization", "  ", 403, 403, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * 核对<code>response.getStatus()</code> 等于 <code>status</code>，<code>body.get("code")</code> 等于 <code>code</code>。
     *
     * @param mvc             传给 <code>mvc.perform()</code> 的 <code>MockMvc</code> 输入
     * @param path            测试请求的资源路径或待访问文件路径
     * @param suppliedMessage 传给 <code>mvc.perform()</code> 的 <code>String</code> 输入
     * @param status          当前 HTTP 或业务流程的状态
     * @param code            用于定位目标测试记录的业务编码
     * @param expectedMessage 传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 为 HTTP 合同提供实际请求路由的 Spring 测试控制器。
     */
    @RestController
    static class Endpoint {
        /**
         * 构造 <code>TokenExpiredException</code> 作为当前测试的输入或结果。
         *
         * @param message 测试传递或用于诊断的消息
         */
        @GetMapping("/authentication")
        void authenticate(@RequestParam("message") String message) {
            throw new TokenExpiredException(message);
        }

        /**
         * 构造 <code>PermissionDeniedException</code> 作为当前测试的输入或结果。
         *
         * @param message 测试传递或用于诊断的消息
         */
        @GetMapping("/authorization")
        void authorize(@RequestParam("message") String message) {
            throw new PermissionDeniedException(message);
        }

        /**
         * 构造 <code>BizRuntimeException</code> 作为当前测试的输入或结果。
         */
        @GetMapping("/business")
        void failBusiness() {
            throw new BizRuntimeException(422, "Invalid order");
        }
    }
}
