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

/**
 * 验证 <code>/authentication</code> 等路由经示例 HTTP 框架执行后的请求行为。
 */
class DefaultWebMvcAuthErrorContractTest {

    /**
     * 用于比较原始就绪信息是否保留的自定义诊断数据。
     */
    private static final Map<String, String> CUSTOM_DETAILS = Collections.singletonMap("source", "custom-translator");

    /**
     * 执行当前 Spring MVC 用例的请求测试入口。
     */
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
            .build();

    /**
     * 创建 <code>new WebError</code> 供当前测试路径调用。
     *
     * @param failure 触发当前测试错误处理路径的失败
     * @return 当前测试使用的结果，取自 <code>new WebError(409, "custom-auth", failure.getClass().getSimpleName(), CUSTOM_DETAILS)</code>
     */
    private static WebError customError(Throwable failure) {
        return new WebError(409, "custom-auth", failure.getClass().getSimpleName(), CUSTOM_DETAILS);
    }

    /**
     * 验证失效令牌映射到 HTTP 未认证状态且保留对应错误说明。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void expiredTokenUsesUnauthorizedHttpStatusAndKeepsMessage() throws Exception {
        assertAuthError("/authentication", ApiCode.UNAUTHORIZED, "Token expired");
    }

    /**
     * 在测试框架中执行 <code>ApiCode.FORBIDDEN.getReason()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void missingPermissionUsesForbiddenHttpStatusAndDefaultReason() throws Exception {
        assertAuthError("/authorization", ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * 在测试框架中执行 <code>MockMvcBuilders.standaloneSetup()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void mvcDispatchUsesTheInjectedTranslatorForBothAuthFailures() throws Exception {
        MockMvc customized = MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(DefaultWebMvcAuthErrorContractTest::customError))
                .build();

        assertCustomAuthError(customized, "/authentication", "TokenExpiredException", CUSTOM_DETAILS);
        assertCustomAuthError(customized, "/authorization", "PermissionDeniedException", CUSTOM_DETAILS);
    }

    /**
     * 在测试框架中执行 <code>context.setServletContext()</code>、<code>context.register()</code>、<code>context.refresh()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
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

    /**
     * 在测试框架中执行 <code>handler.authenticationFailed()</code>、<code>handler.authorizationFailed()</code>、<code>ApiCode.FORBIDDEN.getReason()</code>，验证该场景。
     */
    @Test
    void directAuthResponseMethodsKeepDefaultResponseContract() {
        Ddd4jWebMvcExceptionHandler handler = new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator());
        assertDirectAuthError(handler.authenticationFailed(new TokenExpiredException("Token expired")),
                ApiCode.UNAUTHORIZED, "Token expired");
        assertDirectAuthError(handler.authorizationFailed(new PermissionDeniedException("  ")),
                ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * 核对<code>response.getStatusCode().value()</code> 等于 <code>expected.getCode().intValue()</code>，<code>body.getCode()</code> 等于 <code>expected.getCode()</code>。
     *
     * @param response 当前请求已收到的响应
     * @param expected 当前用例设定的期望值
     * @param message  测试传递或用于诊断的消息
     */
    private void assertDirectAuthError(ResponseEntity<R<String>> response, ApiCode expected, String message) {
        R<String> body = response.getBody();
        assertEquals(expected.getCode().intValue(), response.getStatusCode().value());
        assertEquals(expected.getCode(), body.getCode());
        assertEquals(message, body.getMsg());
    }

    /**
     * 核对<code>response.getStatus()</code> 等于 <code>409</code>，<code>body.get("code")</code> 等于 <code>"custom-auth"</code>。
     *
     * @param customized 传给 <code>customized.perform()</code> 的 <code>MockMvc</code> 输入
     * @param path       测试请求的资源路径或待访问文件路径
     * @param message    测试传递或用于诊断的消息
     * @param details    传给 <code>assertEquals()</code> 的 <code>Map&lt;String, String&gt;</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private void assertCustomAuthError(MockMvc customized, String path, String message,
                                       Map<String, String> details) throws Exception {
        MockHttpServletResponse response = customized.perform(get(path)).andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(409, response.getStatus());
        assertEquals("custom-auth", body.get("code"));
        assertEquals(message, body.get("msg"));
        assertEquals(details, body.get("data"));
    }

    /**
     * 核对<code>response.getStatus()</code> 等于 <code>expected.getCode().intValue()</code>，<code>body.get("code")</code> 等于 <code>expected.getCode()</code>。
     *
     * @param path     测试请求的资源路径或待访问文件路径
     * @param expected 当前用例设定的期望值
     * @param message  测试传递或用于诊断的消息
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private void assertAuthError(String path, ApiCode expected, String message) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(expected.getCode().intValue(), response.getStatus());
        assertEquals(expected.getCode(), body.get("code"));
        assertEquals(message, body.get("msg"));
    }

    /**
     * 向测试容器注册 <code>exceptionTranslator()</code>、<code>nestedMessageSource()</code>、<code>serverI18nProperties()</code> 等测试入口或组件。
     */
    @Configuration
    @EnableWebMvc
    @Import({Endpoint.class, Ddd4jWebMvcExceptionHandler.class, GlobalRestExceptionAdvice.class, GlobalExceptionHandler.class})
    static class CombinedAdviceConfiguration {

        /**
         * 通过 <code>DefaultWebMvcAuthErrorContractTest::customError</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>DefaultWebMvcAuthErrorContractTest::customError</code>
         */
        @Bean
        WebExceptionTranslator exceptionTranslator() {
            return DefaultWebMvcAuthErrorContractTest::customError;
        }

        /**
         * 创建 <code>new NestedMessageSource</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new NestedMessageSource()</code>
         */
        @Bean
        NestedMessageSource nestedMessageSource() {
            return new NestedMessageSource();
        }

        /**
         * 创建 <code>new ServerI18nProperties</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new ServerI18nProperties()</code>
         */
        @Bean
        ServerI18nProperties serverI18nProperties() {
            return new ServerI18nProperties();
        }
    }

    /**
     * 为 HTTP 合同提供实际请求路由的 Spring 测试控制器。
     */
    @RestController
    static class Endpoint {

        /**
         * 构造 <code>TokenExpiredException</code> 作为当前测试的输入或结果。
         */
        @GetMapping("/authentication")
        void authenticate() {
            throw new TokenExpiredException("Token expired");
        }

        /**
         * 构造 <code>PermissionDeniedException</code> 作为当前测试的输入或结果。
         */
        @GetMapping("/authorization")
        void authorize() {
            throw new PermissionDeniedException("  ");
        }
    }
}
