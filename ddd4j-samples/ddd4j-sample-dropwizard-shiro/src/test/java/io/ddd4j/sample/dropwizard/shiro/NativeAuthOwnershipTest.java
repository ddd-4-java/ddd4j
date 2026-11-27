package io.ddd4j.sample.dropwizard.shiro;

import cn.hutool.core.util.ReflectUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 原生全局引擎的 owner 恢复，不覆盖后任。
 */
class NativeAuthOwnershipTest {
    /**
     * \u8c03\u7528 <code>ReflectUtil.getStaticFieldValue()</code>\u3001<code>ReflectUtil.getField()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>SecurityManager</code> \u7ed3\u679c
     */
    private SecurityManager current() {
        return (SecurityManager) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SecurityUtils.class, "securityManager")));
    }

    /** \u9a8c\u8bc1<code>current()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\uff0c<code>current()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\u3002 */
    @Test
    void restoresPreviousAndPreservesReplacement() {
        var previous = current();
        var auth = new NativeAuth();
        auth.close();
        assertSame(previous, current());
        auth = new NativeAuth();
        var replacement = new DefaultSecurityManager();
        SecurityUtils.setSecurityManager(replacement);
        try {
            auth.close();
            assertSame(replacement, current());
        } finally {
            replacement.destroy();
            SecurityUtils.setSecurityManager(previous);
        }
    }

    /** \u9a8c\u8bc1<code>current()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\u3002 */
    @Test
    void leavesCallerProcessManagerInstalledWhileRunning() {
        SecurityManager previous = current();
        DefaultSecurityManager caller = new DefaultSecurityManager();
        SecurityUtils.setSecurityManager(caller);
        try {
            NativeAuth app = new NativeAuth();
            try {
                assertSame(caller, current());
            } finally {
                app.close();
            }
        } finally {
            SecurityUtils.setSecurityManager(previous);
            caller.destroy();
        }
    }

    /** \u9a8c\u8bc1<code>ThreadContext.getSecurityManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>app.manager()</code>\uff0c<code>ThreadContext.getSubject()</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>previous</code>\uff0c<code>ThreadContext.getSubject()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\u3002 */
    @Test
    void restoresWorkerBindingsAndPreservesLaterOwners() {
        SecurityManager originalManager = ThreadContext.getSecurityManager();
        Subject originalSubject = ThreadContext.getSubject();
        DefaultSecurityManager caller = new DefaultSecurityManager();
        Subject previous = new Subject.Builder(caller).buildSubject();
        NativeAuth app = new NativeAuth();
        try {
            ThreadContext.bind(caller);
            ThreadContext.bind(previous);
            try (NativeScope request = new NativeScope(null, app)) {
                assertSame(app.manager(), ThreadContext.getSecurityManager());
                org.junit.jupiter.api.Assertions.assertNotSame(previous, ThreadContext.getSubject());
            }
            assertSame(caller, ThreadContext.getSecurityManager());
            assertSame(previous, ThreadContext.getSubject());
            Subject replacement = new Subject.Builder(caller).buildSubject();
            try (NativeScope request = new NativeScope(null, app)) {
                ThreadContext.bind(caller);
                ThreadContext.bind(replacement);
            }
            assertSame(caller, ThreadContext.getSecurityManager());
            assertSame(replacement, ThreadContext.getSubject());
        } finally {
            app.close();
            ThreadContext.unbindSecurityManager();
            ThreadContext.unbindSubject();
            if (Objects.nonNull(originalManager)) {
                ThreadContext.bind(originalManager);
            }
            if (Objects.nonNull(originalSubject)) {
                ThreadContext.bind(originalSubject);
            }
            caller.destroy();
        }
    }
}
