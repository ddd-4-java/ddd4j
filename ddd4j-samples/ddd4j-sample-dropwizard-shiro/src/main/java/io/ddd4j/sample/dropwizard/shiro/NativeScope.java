package io.ddd4j.sample.dropwizard.shiro;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.util.Objects;

/**
 * 原生 Shiro 工作线程作用域；恢复调用前主体。
 */
final class NativeScope implements AutoCloseable {
    private final Subject before = ThreadContext.getSubject();
    private final Subject owned;
    private final SecurityManager beforeManager = ThreadContext.getSecurityManager();
    private final SecurityManager manager;

    NativeScope(String bearer, NativeAuth auth) {
        manager = auth.manager();
        Subject.Builder builder = new Subject.Builder(manager);
        if (Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            builder.sessionId(bearer.substring(7).trim());
        }
        owned = builder.buildSubject();
        ThreadContext.bind(manager);
        ThreadContext.bind(owned);
    }

    @Override
    public void close() {
        if (ThreadContext.getSubject() == owned) {
            ThreadContext.unbindSubject();
            if (Objects.nonNull(before)) {
                ThreadContext.bind(before);
            }
        }
        if (ThreadContext.getSecurityManager() == manager) {
            ThreadContext.unbindSecurityManager();
            if (Objects.nonNull(beforeManager)) {
                ThreadContext.bind(beforeManager);
            }
        }
    }
}
