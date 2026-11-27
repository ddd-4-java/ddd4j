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

/** \u9a8c\u8bc1 <code>/authentication</code> \u7b49\u8def\u7531\u7ecf\u793a\u4f8b HTTP \u6846\u67b6\u6267\u884c\u540e\u7684\u8bf7\u6c42\u884c\u4e3a\u3002 */
class DefaultWebMvcAuthErrorContractTest {

    /** \u7528\u4e8e\u6bd4\u8f83\u539f\u59cb\u5c31\u7eea\u4fe1\u606f\u662f\u5426\u4fdd\u7559\u7684\u81ea\u5b9a\u4e49\u8bca\u65ad\u6570\u636e\u3002 */
    private static final Map<String, String> CUSTOM_DETAILS = Collections.singletonMap("source", "custom-translator");

    /** \u6267\u884c\u5f53\u524d Spring MVC \u7528\u4f8b\u7684\u8bf7\u6c42\u6d4b\u8bd5\u5165\u53e3\u3002 */
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
            .build();

    /**
     * \u521b\u5efa <code>new WebError</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebError(409, "custom-auth", failure.getClass().getSimpleName(), CUSTOM_DETAILS)</code>
     */
    private static WebError customError(Throwable failure) {
        return new WebError(409, "custom-auth", failure.getClass().getSimpleName(), CUSTOM_DETAILS);
    }

    /**
     * \u9a8c\u8bc1\u5931\u6548\u4ee4\u724c\u6620\u5c04\u5230 HTTP \u672a\u8ba4\u8bc1\u72b6\u6001\u4e14\u4fdd\u7559\u5bf9\u5e94\u9519\u8bef\u8bf4\u660e\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void expiredTokenUsesUnauthorizedHttpStatusAndKeepsMessage() throws Exception {
        assertAuthError("/authentication", ApiCode.UNAUTHORIZED, "Token expired");
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ApiCode.FORBIDDEN.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void missingPermissionUsesForbiddenHttpStatusAndDefaultReason() throws Exception {
        assertAuthError("/authorization", ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>MockMvcBuilders.standaloneSetup()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>context.setServletContext()</code>\u3001<code>context.register()</code>\u3001<code>context.refresh()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>handler.authenticationFailed()</code>\u3001<code>handler.authorizationFailed()</code>\u3001<code>ApiCode.FORBIDDEN.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void directAuthResponseMethodsKeepDefaultResponseContract() {
        Ddd4jWebMvcExceptionHandler handler = new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator());
        assertDirectAuthError(handler.authenticationFailed(new TokenExpiredException("Token expired")),
                ApiCode.UNAUTHORIZED, "Token expired");
        assertDirectAuthError(handler.authorizationFailed(new PermissionDeniedException("  ")),
                ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u6838\u5bf9<code>response.getStatusCode().value()</code> \u7b49\u4e8e <code>expected.getCode().intValue()</code>\uff0c<code>body.getCode()</code> \u7b49\u4e8e <code>expected.getCode()</code>\u3002
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     */
    private void assertDirectAuthError(ResponseEntity<R<String>> response, ApiCode expected, String message) {
        R<String> body = response.getBody();
        assertEquals(expected.getCode().intValue(), response.getStatusCode().value());
        assertEquals(expected.getCode(), body.getCode());
        assertEquals(message, body.getMsg());
    }

    /**
     * \u6838\u5bf9<code>response.getStatus()</code> \u7b49\u4e8e <code>409</code>\uff0c<code>body.get("code")</code> \u7b49\u4e8e <code>"custom-auth"</code>\u3002
     * @param customized \u4f20\u7ed9 <code>customized.perform()</code> \u7684 <code>MockMvc</code> \u8f93\u5165
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @param details \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>Map&lt;String, String&gt;</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u6838\u5bf9<code>response.getStatus()</code> \u7b49\u4e8e <code>expected.getCode().intValue()</code>\uff0c<code>body.get("code")</code> \u7b49\u4e8e <code>expected.getCode()</code>\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private void assertAuthError(String path, ApiCode expected, String message) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
        Map<String, Object> body = JsonKit.toMap(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(expected.getCode().intValue(), response.getStatus());
        assertEquals(expected.getCode(), body.get("code"));
        assertEquals(message, body.get("msg"));
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>exceptionTranslator()</code>\u3001<code>nestedMessageSource()</code>\u3001<code>serverI18nProperties()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @Configuration
    @EnableWebMvc
    @Import({Endpoint.class, Ddd4jWebMvcExceptionHandler.class, GlobalRestExceptionAdvice.class, GlobalExceptionHandler.class})
    static class CombinedAdviceConfiguration {

        /**
         * \u901a\u8fc7 <code>DefaultWebMvcAuthErrorContractTest::customError</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>DefaultWebMvcAuthErrorContractTest::customError</code>
         */
        @Bean
        WebExceptionTranslator exceptionTranslator() {
            return DefaultWebMvcAuthErrorContractTest::customError;
        }

        /**
         * \u521b\u5efa <code>new NestedMessageSource</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new NestedMessageSource()</code>
         */
        @Bean
        NestedMessageSource nestedMessageSource() {
            return new NestedMessageSource();
        }

        /**
         * \u521b\u5efa <code>new ServerI18nProperties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ServerI18nProperties()</code>
         */
        @Bean
        ServerI18nProperties serverI18nProperties() {
            return new ServerI18nProperties();
        }
    }

    /** \u4e3a HTTP \u5408\u540c\u63d0\u4f9b\u5b9e\u9645\u8bf7\u6c42\u8def\u7531\u7684 Spring \u6d4b\u8bd5\u63a7\u5236\u5668\u3002 */
    @RestController
    static class Endpoint {

        /** \u6784\u9020 <code>TokenExpiredException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002 */
        @GetMapping("/authentication")
        void authenticate() {
            throw new TokenExpiredException("Token expired");
        }

        /** \u6784\u9020 <code>PermissionDeniedException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002 */
        @GetMapping("/authorization")
        void authorize() {
            throw new PermissionDeniedException("  ");
        }
    }
}
