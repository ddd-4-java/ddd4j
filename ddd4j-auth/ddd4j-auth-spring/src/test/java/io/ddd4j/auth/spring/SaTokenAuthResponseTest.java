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

/** \u901a\u8fc7 <code>handler.authenticationFailed()</code>\u3001<code>ApiCode.UNAUTHORIZED.getReason()</code>\u3001<code>handler.authorizationFailed()</code> \u9a8c\u8bc1 <code>SaTokenAuthResponse</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class SaTokenAuthResponseTest {

    /** \u5f53\u524d\u7528\u4f8b\u5b9e\u9645\u8c03\u7528\u6216\u914d\u7f6e\u884c\u4e3a\u7684\u5904\u7406\u5668\u3002 */
    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>handler.authenticationFailed()</code>\u3001<code>ApiCode.UNAUTHORIZED.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void preservesAuthenticationMessageOrUsesUnauthorizedReason() {
        assertResponse(handler.authenticationFailed(new TokenExpiredException("请重新登录")), ApiCode.UNAUTHORIZED, "请重新登录");
        assertResponse(handler.authenticationFailed(new TokenExpiredException(" ")), ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>handler.authorizationFailed()</code>\u3001<code>ApiCode.FORBIDDEN.getReason()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void preservesAuthorizationMessageOrUsesForbiddenReason() {
        assertResponse(handler.authorizationFailed(new PermissionDeniedException("没有导出权限")), ApiCode.FORBIDDEN, "没有导出权限");
        assertResponse(handler.authorizationFailed(new PermissionDeniedException(null)), ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    /**
     * \u6838\u5bf9<code>response.getStatusCode().value()</code> \u7b49\u4e8e <code>code.getCode().intValue()</code>\uff0c<code>body</code>\u975e\u7a7a\u3002
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     */
    private void assertResponse(ResponseEntity<R<String>> response, ApiCode code, String message) {
        assertEquals(code.getCode().intValue(), response.getStatusCode().value());
        R<String> body = response.getBody();
        assertNotNull(body);
        assertEquals(code.getCode(), body.getCode());
        assertEquals(message, body.getMsg());
    }
}
