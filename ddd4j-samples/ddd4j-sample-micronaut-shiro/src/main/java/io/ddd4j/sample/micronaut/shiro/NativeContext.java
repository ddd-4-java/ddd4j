package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;

/** 原生请求作用域同时绑定 Shiro 主体和引擎，并恢复参与线程的前任状态。 */
final class NativeContext implements ThreadPropagatedContextElement<ThreadState> {
    private final Subject subject;

    NativeContext(Subject subject) {
        this.subject = subject;
    }

    @Override
    public ThreadState updateThreadContext() {
        // 每次进入线程都创建独立状态，避免跨线程覆写前任资源。
        ThreadState state = new SubjectThreadState(subject);
        state.bind();
        return state;
    }

    @Override
    public void restoreThreadContext(ThreadState state) {
        state.restore();
    }
}
