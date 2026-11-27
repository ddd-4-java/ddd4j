package io.ddd4j.sample.dropwizard.shiro;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 同步 Jersey 调用外围的原生主体作用域；客户端断开仍等待业务 finally。
 */
final class NativeRequestFilter implements Filter {
    /**
     * 提供当前应用原生认证组件的延迟引用。
     */
    private final Supplier<NativeAuth> auth;
    /**
     * 通知观察者请求标识及线程上下文是否清理的回调。
     */
    private final BiConsumer<String, Boolean> completed;

    /**
     * 装配验证请求线程的原生认证过滤器。
     *
     * @param auth      当前应用的原生认证组件
     * @param completed 观察请求及上下文清理结果的回调
     */
    NativeRequestFilter(Supplier<NativeAuth> auth, BiConsumer<String, Boolean> completed) {
        this.auth = auth;
        this.completed = completed;
    }

    /**
     * 在原生认证作用域内执行 Servlet 请求，并在退出后通知观察回调。
     *
     * @param request  进入过滤器的 Servlet 请求
     * @param response 传给过滤器链的 Servlet 响应
     * @param chain    进入业务处理的 Servlet 过滤器链
     * @throws IOException      读取请求或输出响应时发生 I/O 错误
     * @throws ServletException Servlet 过滤器链处理失败
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var http = (HttpServletRequest) request;
        var before = new java.util.HashMap<>(io.ddd4j.core.context.ThreadContext.getResources());
        Subject previousSubject = ThreadContext.getSubject();
        SecurityManager previousManager = ThreadContext.getSecurityManager();
        try (var scope = new NativeScope(http.getHeader("Authorization"), auth.get())) {
            chain.doFilter(request, response);
        } finally {
            String id = http.getHeader("X-Request-Id");
            if (Objects.nonNull(id)) {
                boolean clean = ThreadContext.getSubject() == previousSubject
                        && ThreadContext.getSecurityManager() == previousManager && before.equals(io.ddd4j.core.context.ThreadContext.getResources());
                completed.accept(id, clean);
            }
        }
    }
}
