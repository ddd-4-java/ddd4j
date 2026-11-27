package io.ddd4j.sample.vertx.shiro;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.util.Objects;

/**
 * 原生 Shiro 工作线程作用域；恢复调用前主体。
 */
final class NativeScope implements AutoCloseable {
    /**
     * 进入请求之前线程绑定的Shiro 主体。
     */
    private final Subject before = ThreadContext.getSubject();
    /**
     * 当前请求绑定到当前线程的Shiro 主体。
     */
    private final Subject owned;
    /**
     * 进入请求作用域前线程绑定的 Shiro 安全管理器。
     */
    private final SecurityManager beforeManager = ThreadContext.getSecurityManager();
    /**
     * 作用域内绑定给当前线程的 Shiro 安全管理器。
     */
    private final SecurityManager manager;

    /**
     * 将原生认证上下文绑定到当前请求线程并保存进入前的绑定。
     *
     * @param bearer 请求中的 Bearer 认证头
     * @param auth   当前应用的原生认证组件
     */
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

    /**
     * 结束当前请求的认证作用域，按进入时保存的状态恢复工作线程。
     */
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
