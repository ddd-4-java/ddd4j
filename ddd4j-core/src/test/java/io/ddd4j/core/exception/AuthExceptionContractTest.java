package io.ddd4j.core.exception;

import io.ddd4j.core.api.ApiCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthExceptionContractTest {

    @Test
    void authenticationFailuresCarryUnauthorizedCodeAndOriginalCause() {
        assertEquals(BizRuntimeException.class, AuthenticationException.class.getSuperclass());
        RuntimeException cause = new IllegalStateException("会话已失效");
        assertFailure(new AuthenticationException("请重新登录"), ApiCode.UNAUTHORIZED, "请重新登录", null);
        assertFailure(new TokenExpiredException("令牌过期", cause), ApiCode.UNAUTHORIZED, "令牌过期", cause);
    }

    @Test
    void authorizationFailuresCarryForbiddenCodeAndOriginalCause() {
        assertEquals(BizRuntimeException.class, AuthorizationException.class.getSuperclass());
        RuntimeException cause = new IllegalStateException("角色权限已变更");
        assertFailure(new AuthorizationException("  "), ApiCode.FORBIDDEN, "  ", null);
        assertFailure(new PermissionDeniedException("无权导出", cause), ApiCode.FORBIDDEN, "无权导出", cause);
    }

    private void assertFailure(RuntimeException failure, ApiCode code, String message, Throwable cause) {
        BizRuntimeException businessFailure = assertInstanceOf(BizRuntimeException.class, failure);
        assertEquals(code.getCode(), businessFailure.getCode());
        assertEquals(message, businessFailure.getMessage());
        assertSame(cause, businessFailure.getCause());
    }
}
