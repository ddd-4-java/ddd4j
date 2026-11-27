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
    /** \u901a\u77e5\u89c2\u5bdf\u8005\u8bf7\u6c42\u6807\u8bc6\u53ca\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u662f\u5426\u6e05\u7406\u7684\u56de\u8c03\u3002 */
    private final BiConsumer<String, Boolean> completed;

    /**
     * \u88c5\u914d\u9a8c\u8bc1\u8bf7\u6c42\u7ebf\u7a0b\u7684\u539f\u751f\u8ba4\u8bc1\u8fc7\u6ee4\u5668\u3002
     * @param completed \u89c2\u5bdf\u8bf7\u6c42\u53ca\u4e0a\u4e0b\u6587\u6e05\u7406\u7ed3\u679c\u7684\u56de\u8c03
     */
    NativeRequestFilter(BiConsumer<String, Boolean> completed) {
        this.completed = completed;
    }

    /**
     * \u5728\u539f\u751f\u8ba4\u8bc1\u4f5c\u7528\u57df\u5185\u6267\u884c Servlet \u8bf7\u6c42\uff0c\u5e76\u5728\u9000\u51fa\u540e\u901a\u77e5\u89c2\u5bdf\u56de\u8c03\u3002
     * @param request \u8fdb\u5165\u8fc7\u6ee4\u5668\u7684 Servlet \u8bf7\u6c42
     * @param response \u4f20\u7ed9\u8fc7\u6ee4\u5668\u94fe\u7684 Servlet \u54cd\u5e94
     * @param chain \u8fdb\u5165\u4e1a\u52a1\u5904\u7406\u7684 Servlet \u8fc7\u6ee4\u5668\u94fe
     * @throws IOException \u8bfb\u53d6\u8bf7\u6c42\u6216\u8f93\u51fa\u54cd\u5e94\u65f6\u53d1\u751f I/O \u9519\u8bef
     * @throws ServletException Servlet \u8fc7\u6ee4\u5668\u94fe\u5904\u7406\u5931\u8d25
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
