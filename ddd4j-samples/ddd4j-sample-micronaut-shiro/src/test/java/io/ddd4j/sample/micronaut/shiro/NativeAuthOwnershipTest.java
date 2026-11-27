package io.ddd4j.sample.micronaut.shiro;

import cn.hutool.core.util.ReflectUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.util.ThreadState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 Shiro 请求引擎和调用方引擎的所有权边界。
 */
class NativeAuthOwnershipTest {
    /** \u9a8c\u8bc1<code>SecurityUtils.getSecurityManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\u3002 */
    @Test
    void applicationKeepsCallerVmManager() {
        SecurityManager previous = (SecurityManager) ReflectUtil.getStaticFieldValue(
                ReflectUtil.getField(SecurityUtils.class, "securityManager"));
        DefaultSecurityManager caller = new DefaultSecurityManager();
        SecurityUtils.setSecurityManager(caller);
        NativeAuth auth = new NativeAuth();
        try {
            assertSame(caller, SecurityUtils.getSecurityManager());
        } finally {
            auth.close();
            try {
                assertSame(caller, SecurityUtils.getSecurityManager());
            } finally {
                SecurityUtils.setSecurityManager(previous);
                caller.destroy();
            }
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SecurityUtils.getSubject()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>request</code>\uff0c<code>ThreadContext.getSecurityManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\u3002 */
    @Test
    void routeRestoresCallerThreadAfterFailure() {
        NativeAuth auth = new NativeAuth();
        DefaultSecurityManager caller = new DefaultSecurityManager();
        Subject originalSubject = ThreadContext.getSubject();
        SecurityManager originalManager = ThreadContext.getSecurityManager();
        Subject previous = new Subject.Builder(caller).buildSubject();
        ThreadContext.bind(previous);
        ThreadContext.bind(caller);
        try {
            Subject request = new Subject.Builder(auth.manager()).buildSubject();
            NativeContext scope = new NativeContext(request);
            IllegalStateException failure = new IllegalStateException("route failed");
            assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                ThreadState state = scope.updateThreadContext();
                try {
                    assertSame(request, SecurityUtils.getSubject());
                    assertSame(auth.manager(), SecurityUtils.getSecurityManager());
                    throw failure;
                } finally {
                    scope.restoreThreadContext(state);
                }
            }));
            assertSame(previous, ThreadContext.getSubject());
            assertSame(caller, ThreadContext.getSecurityManager());
        } finally {
            ThreadContext.unbindSubject();
            ThreadContext.unbindSecurityManager();
            if (java.util.Objects.nonNull(originalSubject)) {
                ThreadContext.bind(originalSubject);
            }
            if (java.util.Objects.nonNull(originalManager)) {
                ThreadContext.bind(originalManager);
            }
            auth.close();
            caller.destroy();
        }
    }
}
