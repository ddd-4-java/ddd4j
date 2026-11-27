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

/**
 * 验证 <code>/authentication</code> 等路由经示例 HTTP 框架执行后的请求行为。
 */
class AuthExceptionResponseTest {

    /**
     * 构造 <code>GlobalExceptionHandler</code>、<code>NestedMessageSource</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>Stream&lt;Object&gt;</code> 结果
     */
    static Stream<Object> advice() {
        return Stream.of(new GlobalExceptionHandler(new NestedMessageSource(), new ServerI18nProperties()),
                new GlobalRestExceptionAdvice(),
                new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()));
    }

    /**
     * 在测试框架中执行 <code>MockMvcBuilders.standaloneSetup()</code>、<code>ApiCode.UNAUTHORIZED.getReason()</code>、<code>ApiCode.FORBIDDEN.getReason()</code>，验证该场景。
     *
     * @param advice 传给 <code>MockMvcBuilders.standaloneSetup()</code> 的 <code>Object</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 核对<code>response.getStatus()</code> 等于 <code>code.getCode().intValue()</code>，<code>body.required("code").asInt()</code> 等于 <code>code.getCode().intValue()</code>。
     *
     * @param mvc      传给 <code>mvc.perform()</code> 的 <code>MockMvc</code> 输入
     * @param path     测试请求的资源路径或待访问文件路径
     * @param message  测试传递或用于诊断的消息
     * @param code     用于定位目标测试记录的业务编码
     * @param expected 当前用例设定的期望值
     * @throws Exception 处理测试数据的序列化时失败
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
        void authenticate(@RequestParam(name = "message", required = false) String message) {
            throw new TokenExpiredException(message);
        }

        /**
         * 构造 <code>PermissionDeniedException</code> 作为当前测试的输入或结果。
         *
         * @param message 测试传递或用于诊断的消息
         */
        @GetMapping("/authorization")
        void authorize(@RequestParam(name = "message", required = false) String message) {
            throw new PermissionDeniedException(message);
        }
    }
}
