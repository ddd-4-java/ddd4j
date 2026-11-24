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

class ShiroRuntimeOwnershipTest {

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

    private static ShiroRuntimeConfig runtime() {
        ShiroRuntimeConfig runtime = new ShiroRuntimeConfig();
        runtime.userRepository = new InMemoryUserRepository();
        runtime.roleRepository = new InMemoryRoleRepository();
        return runtime;
    }

    private static SecurityManager processManager() {
        try {
            return SecurityUtils.getSecurityManager();
        } catch (UnavailableSecurityManagerException absent) {
            return null;
        }
    }
}
