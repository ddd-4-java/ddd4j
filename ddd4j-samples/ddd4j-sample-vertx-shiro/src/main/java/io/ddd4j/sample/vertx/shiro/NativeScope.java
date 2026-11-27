package io.ddd4j.sample.vertx.shiro;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.util.Objects;

/**
 * 原生 Shiro 工作线程作用域；恢复调用前主体。
 */
final class NativeScope implements AutoCloseable {
    /** \u8fdb\u5165\u8bf7\u6c42\u4e4b\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684Shiro \u4e3b\u4f53\u3002 */
    private final Subject before = ThreadContext.getSubject();
    /** \u5f53\u524d\u8bf7\u6c42\u7ed1\u5b9a\u5230\u5f53\u524d\u7ebf\u7a0b\u7684Shiro \u4e3b\u4f53\u3002 */
    private final Subject owned;
    /** \u8fdb\u5165\u8bf7\u6c42\u4f5c\u7528\u57df\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002 */
    private final SecurityManager beforeManager = ThreadContext.getSecurityManager();
    /** \u4f5c\u7528\u57df\u5185\u7ed1\u5b9a\u7ed9\u5f53\u524d\u7ebf\u7a0b\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002 */
    private final SecurityManager manager;

    /**
     * \u5c06\u539f\u751f\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7ed1\u5b9a\u5230\u5f53\u524d\u8bf7\u6c42\u7ebf\u7a0b\u5e76\u4fdd\u5b58\u8fdb\u5165\u524d\u7684\u7ed1\u5b9a\u3002
     * @param bearer \u8bf7\u6c42\u4e2d\u7684 Bearer \u8ba4\u8bc1\u5934
     * @param auth \u5f53\u524d\u5e94\u7528\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6
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

    /** \u7ed3\u675f\u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u4f5c\u7528\u57df\uff0c\u6309\u8fdb\u5165\u65f6\u4fdd\u5b58\u7684\u72b6\u6001\u6062\u590d\u5de5\u4f5c\u7ebf\u7a0b\u3002 */
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
