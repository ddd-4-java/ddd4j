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

/**
 * 验证 <code>ShiroRuntimeOwnership</code> 在 <code>restoresProcessManager</code> 等场景中的行为。
 */
class ShiroRuntimeOwnershipTest {

    /**
     * 读取 <code>runtime</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>runtime</code>
     */
    private static ShiroRuntimeConfig runtime() {
        ShiroRuntimeConfig runtime = new ShiroRuntimeConfig();
        runtime.userRepository = new InMemoryUserRepository();
        runtime.roleRepository = new InMemoryRoleRepository();
        return runtime;
    }

    /**
     * 调用 <code>SecurityUtils.getSecurityManager()</code> 执行当前夹具操作。
     *
     * @return 根据测试条件返回的可能值，包括 <code>SecurityUtils.getSecurityManager()</code>、<code>null</code>
     */
    private static SecurityManager processManager() {
        try {
            return SecurityUtils.getSecurityManager();
        } catch (UnavailableSecurityManagerException absent) {
            return null;
        }
    }

    /**
     * 验证<code>ThreadContext.getSecurityManager()</code> 与…为同一实例 <code>threadOwner</code>，<code>ThreadContext.unbindSecurityManager()</code> 与…为同一实例 <code>threadOwner</code>，<code>processManager()</code> 与…为同一实例 <code>previous</code>。
     */
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

    /**
     * 验证<code>ThreadContext.unbindSecurityManager()</code> 与…为同一实例 <code>sampleManager</code>，<code>processManager()</code> 与…为同一实例 <code>replacement</code>。
     */
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
