package io.ddd4j.core.exception;

import io.ddd4j.core.api.ApiCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>AuthenticationException.class.getSuperclass()</code>\u3001<code>AuthorizationException.class.getSuperclass()</code> \u9a8c\u8bc1 <code>AuthExceptionContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class AuthExceptionContractTest {

    /** \u9a8c\u8bc1<code>AuthenticationException.class.getSuperclass()</code> \u7b49\u4e8e <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void authenticationFailuresCarryUnauthorizedCodeAndOriginalCause() {
        assertEquals(BizRuntimeException.class, AuthenticationException.class.getSuperclass());
        RuntimeException cause = new IllegalStateException("会话已失效");
        assertFailure(new AuthenticationException("请重新登录"), ApiCode.UNAUTHORIZED, "请重新登录", null);
        assertFailure(new TokenExpiredException("令牌过期", cause), ApiCode.UNAUTHORIZED, "令牌过期", cause);
    }

    /** \u9a8c\u8bc1<code>AuthorizationException.class.getSuperclass()</code> \u7b49\u4e8e <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void authorizationFailuresCarryForbiddenCodeAndOriginalCause() {
        assertEquals(BizRuntimeException.class, AuthorizationException.class.getSuperclass());
        RuntimeException cause = new IllegalStateException("角色权限已变更");
        assertFailure(new AuthorizationException("  "), ApiCode.FORBIDDEN, "  ", null);
        assertFailure(new PermissionDeniedException("无权导出", cause), ApiCode.FORBIDDEN, "无权导出", cause);
    }

    /**
     * \u6838\u5bf9<code>businessFailure.getCode()</code> \u7b49\u4e8e <code>code.getCode()</code>\uff0c<code>businessFailure.getMessage()</code> \u7b49\u4e8e <code>message</code>\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @param cause \u4f20\u7ed9 <code>assertSame()</code> \u7684 <code>Throwable</code> \u8f93\u5165
     */
    private void assertFailure(RuntimeException failure, ApiCode code, String message, Throwable cause) {
        BizRuntimeException businessFailure = assertInstanceOf(BizRuntimeException.class, failure);
        assertEquals(code.getCode(), businessFailure.getCode());
        assertEquals(message, businessFailure.getMessage());
        assertSame(cause, businessFailure.getCause());
    }
}
