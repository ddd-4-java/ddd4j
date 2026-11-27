package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;

/** 原生请求作用域同时绑定 Shiro 主体和引擎，并恢复参与线程的前任状态。 */
final class NativeContext implements ThreadPropagatedContextElement<ThreadState> {
    /** \u5f53\u524d\u8fd0\u884c\u8ba4\u8bc1\u903b\u8f91\u4f7f\u7528\u7684\u539f\u751f\u4e3b\u4f53\u3002 */
    private final Subject subject;

    /**
     * \u4e3a\u5f53\u524d\u8ba4\u8bc1\u8bf7\u6c42\u5efa\u7acb\u53ef\u4f20\u64ad\u5230\u5de5\u4f5c\u7ebf\u7a0b\u7684\u539f\u751f\u4e0a\u4e0b\u6587\u5bf9\u8c61\u3002
     * @param subject \u5f53\u524d\u8bf7\u6c42\u4f7f\u7528\u7684\u539f\u751f Shiro \u4e3b\u4f53
     */
    NativeContext(Subject subject) {
        this.subject = subject;
    }

    /**
     * \u5728\u6267\u884c\u8ba4\u8bc1\u903b\u8f91\u7684\u5de5\u4f5c\u7ebf\u7a0b\u4e0a\u5b89\u88c5\u5f53\u524d\u8bf7\u6c42\u7684\u539f\u751f\u4e0a\u4e0b\u6587\u3002
     * @return \u9000\u51fa\u5f53\u524d\u8c03\u7528\u65f6\u7528\u4e8e\u6062\u590d\u7684\u72b6\u6001
     */
    @Override
    public ThreadState updateThreadContext() {
        // 每次进入线程都创建独立状态，避免跨线程覆写前任资源。
        ThreadState state = new SubjectThreadState(subject);
        state.bind();
        return state;
    }

    /**
     * \u6062\u590d\u8fdb\u5165\u5f53\u524d\u8ba4\u8bc1\u5de5\u4f5c\u7ebf\u7a0b\u4e4b\u524d\u7684\u539f\u751f\u72b6\u6001\u3002
     * @param state \u8981\u6062\u590d\u7684\u539f\u7ebf\u7a0b\u72b6\u6001
     */
    @Override
    public void restoreThreadContext(ThreadState state) {
        state.restore();
    }
}
