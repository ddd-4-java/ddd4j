package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;

/**
 * 原生请求作用域同时绑定 Shiro 主体和引擎，并恢复参与线程的前任状态。
 */
final class NativeContext implements ThreadPropagatedContextElement<ThreadState> {
    /**
     * 当前运行认证逻辑使用的原生主体。
     */
    private final Subject subject;

    /**
     * 为当前认证请求建立可传播到工作线程的原生上下文对象。
     *
     * @param subject 当前请求使用的原生 Shiro 主体
     */
    NativeContext(Subject subject) {
        this.subject = subject;
    }

    /**
     * 在执行认证逻辑的工作线程上安装当前请求的原生上下文。
     *
     * @return 退出当前调用时用于恢复的状态
     */
    @Override
    public ThreadState updateThreadContext() {
        // 每次进入线程都创建独立状态，避免跨线程覆写前任资源。
        ThreadState state = new SubjectThreadState(subject);
        state.bind();
        return state;
    }

    /**
     * 恢复进入当前认证工作线程之前的原生状态。
     *
     * @param state 要恢复的原线程状态
     */
    @Override
    public void restoreThreadContext(ThreadState state) {
        state.restore();
    }
}
