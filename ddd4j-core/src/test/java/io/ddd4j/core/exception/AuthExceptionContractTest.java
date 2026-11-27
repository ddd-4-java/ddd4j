package io.ddd4j.core.exception;

import io.ddd4j.core.api.ApiCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>AuthenticationException.class.getSuperclass()</code>、<code>AuthorizationException.class.getSuperclass()</code> 验证 <code>AuthExceptionContract</code> 的运行行为。
 */
class AuthExceptionContractTest {

    /**
     * 验证<code>AuthenticationException.class.getSuperclass()</code> 等于 <code>BizRuntimeException.class</code>。
     */
    @Test
    void authenticationFailuresCarryUnauthorizedCodeAndOriginalCause() {
        assertEquals(BizRuntimeException.class, AuthenticationException.class.getSuperclass());
        RuntimeException cause = new IllegalStateException("会话已失效");
        assertFailure(new AuthenticationException("请重新登录"), ApiCode.UNAUTHORIZED, "请重新登录", null);
        assertFailure(new TokenExpiredException("令牌过期", cause), ApiCode.UNAUTHORIZED, "令牌过期", cause);
    }

    /**
     * 验证<code>AuthorizationException.class.getSuperclass()</code> 等于 <code>BizRuntimeException.class</code>。
     */
    @Test
    void authorizationFailuresCarryForbiddenCodeAndOriginalCause() {
        assertEquals(BizRuntimeException.class, AuthorizationException.class.getSuperclass());
        RuntimeException cause = new IllegalStateException("角色权限已变更");
        assertFailure(new AuthorizationException("  "), ApiCode.FORBIDDEN, "  ", null);
        assertFailure(new PermissionDeniedException("无权导出", cause), ApiCode.FORBIDDEN, "无权导出", cause);
    }

    /**
     * 核对<code>businessFailure.getCode()</code> 等于 <code>code.getCode()</code>，<code>businessFailure.getMessage()</code> 等于 <code>message</code>。
     *
     * @param failure 触发当前测试错误处理路径的失败
     * @param code    用于定位目标测试记录的业务编码
     * @param message 测试传递或用于诊断的消息
     * @param cause   传给 <code>assertSame()</code> 的 <code>Throwable</code> 输入
     */
    private void assertFailure(RuntimeException failure, ApiCode code, String message, Throwable cause) {
        BizRuntimeException businessFailure = assertInstanceOf(BizRuntimeException.class, failure);
        assertEquals(code.getCode(), businessFailure.getCode());
        assertEquals(message, businessFailure.getMessage());
        assertSame(cause, businessFailure.getCause());
    }
}
