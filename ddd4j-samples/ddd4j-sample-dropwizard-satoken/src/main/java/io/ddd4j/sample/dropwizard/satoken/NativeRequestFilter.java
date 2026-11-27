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
    private final BiConsumer<String, Boolean> completed;

    NativeRequestFilter(BiConsumer<String, Boolean> completed) {
        this.completed = completed;
    }

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
