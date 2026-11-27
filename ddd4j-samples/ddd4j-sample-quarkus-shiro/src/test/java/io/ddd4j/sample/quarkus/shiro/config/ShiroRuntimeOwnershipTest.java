package io.ddd4j.sample.quarkus.shiro.config;

import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.quarkus.shiro.rbac.InMemoryUserRepository;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertSame;

/** \u9a8c\u8bc1 <code>ShiroRuntimeOwnership</code> \u5728 <code>restoresProcessManager</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class ShiroRuntimeOwnershipTest {

    /**
     * \u8bfb\u53d6 <code>runtime</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>runtime</code>
     */
    private static ShiroRuntimeConfig runtime() {
        ShiroRuntimeConfig runtime = new ShiroRuntimeConfig();
        runtime.userRepository = new InMemoryUserRepository();
        runtime.roleRepository = new InMemoryRoleRepository();
        return runtime;
    }

    /**
     * \u8c03\u7528 <code>SecurityUtils.getSecurityManager()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>SecurityUtils.getSecurityManager()</code>\u3001<code>null</code>
     */
    private static SecurityManager processManager() {
        try {
            return SecurityUtils.getSecurityManager();
        } catch (UnavailableSecurityManagerException absent) {
            return null;
        }
    }

    /** \u9a8c\u8bc1<code>ThreadContext.getSecurityManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>threadOwner</code>\uff0c<code>ThreadContext.unbindSecurityManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>threadOwner</code>\uff0c<code>processManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
    @Test
    void restoresProcessManagerWithAnUnrelatedThreadBinding() {
        SecurityManager originalThread = ThreadContext.unbindSecurityManager();
        SecurityManager originalGlobal = processManager();
        DefaultSecurityManager previous = new DefaultSecurityManager();
        DefaultSecurityManager threadOwner = new DefaultSecurityManager();
        ShiroRuntimeConfig runtime = runtime();
        try {
            SecurityUtils.setSecurityManager(previous);
            ThreadContext.bind(threadOwner);
            runtime.onStart(null);
            assertSame(threadOwner, ThreadContext.getSecurityManager());

            runtime.onStop(null);
            assertSame(threadOwner, ThreadContext.unbindSecurityManager());
            assertSame(previous, processManager(), "sample close must restore the prior process manager");
        } finally {
            runtime.destroy();
            SecurityUtils.setSecurityManager(originalGlobal);
            if (Objects.nonNull(originalThread)) {
                ThreadContext.bind(originalThread);
            }
            threadOwner.destroy();
            previous.destroy();
        }
    }

    /** \u9a8c\u8bc1<code>ThreadContext.unbindSecurityManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>sampleManager</code>\uff0c<code>processManager()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\u3002 */
    @Test
    void leavesReplacementProcessManagerInPlaceWhileThreadRetainsSampleManager() {
        SecurityManager originalThread = ThreadContext.unbindSecurityManager();
        SecurityManager originalGlobal = processManager();
        DefaultSecurityManager replacement = new DefaultSecurityManager();
        ShiroRuntimeConfig runtime = runtime();
        try {
            SecurityUtils.setSecurityManager(null);
            runtime.onStart(null);
            SecurityManager sampleManager = processManager();
            ThreadContext.bind(sampleManager);
            SecurityUtils.setSecurityManager(replacement);

            runtime.onStop(null);
            assertSame(sampleManager, ThreadContext.unbindSecurityManager());
            assertSame(replacement, processManager(), "sample close must preserve the newer process manager");
        } finally {
            runtime.destroy();
            SecurityUtils.setSecurityManager(originalGlobal);
            if (Objects.nonNull(originalThread)) {
                ThreadContext.bind(originalThread);
            }
            replacement.destroy();
        }
    }
}
