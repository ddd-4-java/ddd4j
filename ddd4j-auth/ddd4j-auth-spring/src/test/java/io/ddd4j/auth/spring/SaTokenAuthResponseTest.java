package io.ddd4j.auth.spring;

import io.ddd4j.auth.spring.satoken.SaTokenExceptionHandler;
import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.core.exception.PermissionDeniedException;
import io.ddd4j.core.exception.TokenExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 通过 <code>handler.authenticationFailed()</code>、<code>ApiCode.UNAUTHORIZED.getReason()</code>、<code>handler.authorizationFailed()</code> 验证 <code>SaTokenAuthResponse</code> 的运行行为。
 */
class SaTokenAuthResponseTest {

    /**
     * 当前用例实际调用或配置行为的处理器。
     */
    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();

    /**
     * 在测试框架中执行 <code>handler.authenticationFailed()</code>、<code>ApiCode.UNAUTHORIZED.getReason()</code>，验证该场景。
     */
    @Test
    void preservesAuthenticationMessageOrUsesUnauthorizedReason() {
        assertResponse(handler.authenticationFailed(new TokenExpiredException("请重新登录")), ApiCode.UNAUTHORIZED, "请重新登录");
        assertResponse(handler.authenticationFailed(new TokenExpiredException(" ")), ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
    }

    /**
     * 在测试框架中执行 <code>handler.authorizationFailed()</code>、<code>ApiCode.FORBIDDEN.getReason()</code>，验证该场景。
     */
    @Test
    void preservesAuthorizationMessageOrUsesForbiddenReason() {
        assertResponse(handler.authorizationFailed(new PermissionDeniedException("没有导出权限")), ApiCode.FORBIDDEN, "没有导出权限");
        assertResponse(handler.authorizationFailed(new PermissionDeniedException(null)), ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * 核对<code>response.getStatusCode().value()</code> 等于 <code>code.getCode().intValue()</code>，<code>body</code>非空。
     *
     * @param response 当前请求已收到的响应
     * @param code     用于定位目标测试记录的业务编码
     * @param message  测试传递或用于诊断的消息
     */
    private void assertResponse(ResponseEntity<R<String>> response, ApiCode code, String message) {
        assertEquals(code.getCode().intValue(), response.getStatusCode().value());
        R<String> body = response.getBody();
        assertNotNull(body);
        assertEquals(code.getCode(), body.getCode());
        assertEquals(message, body.getMsg());
    }
}
