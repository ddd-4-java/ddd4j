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
     * 调用 <code>ReflectUtil.getStaticFieldValue()</code>、<code>ReflectUtil.getField()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>SecurityManager</code> 结果
     */
    private SecurityManager current() {
        return (SecurityManager) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SecurityUtils.class, "securityManager")));
    }

    /**
     * 验证<code>current()</code> 与…为同一实例 <code>previous</code>，<code>current()</code> 与…为同一实例 <code>replacement</code>。
     */
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

    /**
     * 验证<code>current()</code> 与…为同一实例 <code>caller</code>。
     */
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

    /**
     * 验证<code>ThreadContext.getSecurityManager()</code> 与…为同一实例 <code>app.manager()</code>，<code>ThreadContext.getSubject()</code> 与…为不同的实例 <code>previous</code>，<code>ThreadContext.getSubject()</code> 与…为同一实例 <code>replacement</code>。
     */
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
