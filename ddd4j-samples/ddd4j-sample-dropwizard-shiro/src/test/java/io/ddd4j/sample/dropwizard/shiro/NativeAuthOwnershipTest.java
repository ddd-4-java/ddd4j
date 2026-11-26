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
    private SecurityManager current() {
        return (SecurityManager) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SecurityUtils.class, "securityManager")));
    }

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
