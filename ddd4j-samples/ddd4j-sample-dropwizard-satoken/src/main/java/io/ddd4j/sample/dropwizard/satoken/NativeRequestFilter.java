package io.ddd4j.sample.dropwizard.satoken;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.context.model.SaTokenContextModelBox;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 同步 Jersey 调用外围的原生主体作用域；客户端断开仍等待业务 finally。
 */
final class NativeRequestFilter implements Filter {
    /**
     * 通知观察者请求标识及线程上下文是否清理的回调。
     */
    private final BiConsumer<String, Boolean> completed;

    /**
     * 装配验证请求线程的原生认证过滤器。
     *
     * @param completed 观察请求及上下文清理结果的回调
     */
    NativeRequestFilter(BiConsumer<String, Boolean> completed) {
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
        SaTokenContextModelBox previousRequest = SaTokenContextForThreadLocalStaff.modelBoxThreadLocal.get();
        try (var scope = new NativeScope(http, (HttpServletResponse) response)) {
            chain.doFilter(request, response);
        } finally {
            String id = http.getHeader("X-Request-Id");
            if (Objects.nonNull(id)) {
                boolean clean = SaTokenContextForThreadLocalStaff.modelBoxThreadLocal.get() == previousRequest && before.equals(io.ddd4j.core.context.ThreadContext.getResources());
                completed.accept(id, clean);
            }
        }
    }
}
