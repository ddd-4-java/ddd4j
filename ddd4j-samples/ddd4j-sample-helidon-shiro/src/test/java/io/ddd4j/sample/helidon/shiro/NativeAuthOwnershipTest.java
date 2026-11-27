package io.ddd4j.sample.helidon.shiro;

import cn.hutool.core.util.ReflectUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 验证 Shiro 请求引擎和调用方引擎的所有权边界。
 */
class NativeAuthOwnershipTest {
    /**
     * 验证<code>SecurityUtils.getSecurityManager()</code> 与…为同一实例 <code>caller</code>。
     */
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

    /**
     * 验证<code>SecurityUtils.getSubject()</code> 与…为不同的实例 <code>previous</code>，<code>SecurityUtils.getSecurityManager()</code> 与…为同一实例 <code>auth.manager()</code>，<code>ThreadContext.getSecurityManager()</code> 与…为同一实例 <code>caller</code>。
     */
    @Test
    void requestFiltersRestoreCallerThreadBindings() {
        NativeAuth auth = new NativeAuth();
        DefaultSecurityManager caller = new DefaultSecurityManager();
        Subject originalSubject = ThreadContext.getSubject();
        SecurityManager originalManager = ThreadContext.getSecurityManager();
        Subject previous = new Subject.Builder(caller).buildSubject();
        ThreadContext.bind(previous);
        ThreadContext.bind(caller);
        try {
            NativeAuthFilter filter = new NativeAuthFilter();
            filter.auth = auth;
            URI base = URI.create("http://localhost/");
            ContainerRequest request = new ContainerRequest(base, base.resolve("auth/me"), "GET", null,
                    new MapPropertiesDelegate(), null);
            filter.filter(request);
            try {
                assertNotSame(previous, SecurityUtils.getSubject());
                assertSame(auth.manager(), SecurityUtils.getSecurityManager());
            } finally {
                filter.filter(request, null);
            }
            assertSame(previous, ThreadContext.getSubject());
            assertSame(caller, ThreadContext.getSecurityManager());
        } finally {
            ThreadContext.unbindSubject();
            ThreadContext.unbindSecurityManager();
            if (Objects.nonNull(originalSubject)) {
                ThreadContext.bind(originalSubject);
            }
            if (Objects.nonNull(originalManager)) {
                ThreadContext.bind(originalManager);
            }
            auth.close();
            caller.destroy();
        }
    }
}
