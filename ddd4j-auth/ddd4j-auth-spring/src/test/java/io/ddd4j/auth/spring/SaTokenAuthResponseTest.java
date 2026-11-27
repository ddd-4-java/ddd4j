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

class SaTokenAuthResponseTest {

    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();

    @Test
    void preservesAuthenticationMessageOrUsesUnauthorizedReason() {
        assertResponse(handler.authenticationFailed(new TokenExpiredException("请重新登录")), ApiCode.UNAUTHORIZED, "请重新登录");
        assertResponse(handler.authenticationFailed(new TokenExpiredException(" ")), ApiCode.UNAUTHORIZED, ApiCode.UNAUTHORIZED.getReason());
    }

    @Test
    void preservesAuthorizationMessageOrUsesForbiddenReason() {
        assertResponse(handler.authorizationFailed(new PermissionDeniedException("没有导出权限")), ApiCode.FORBIDDEN, "没有导出权限");
        assertResponse(handler.authorizationFailed(new PermissionDeniedException(null)), ApiCode.FORBIDDEN, ApiCode.FORBIDDEN.getReason());
    }

    private void assertResponse(ResponseEntity<R<String>> response, ApiCode code, String message) {
        assertEquals(code.getCode().intValue(), response.getStatusCode().value());
        R<String> body = response.getBody();
        assertNotNull(body);
        assertEquals(code.getCode(), body.getCode());
        assertEquals(message, body.getMsg());
    }
}
