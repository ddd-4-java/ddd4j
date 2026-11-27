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
    /** \u63d0\u4f9b\u5f53\u524d\u5e94\u7528\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u7684\u5ef6\u8fdf\u5f15\u7528\u3002 */
    private final Supplier<NativeAuth> auth;
    /** \u901a\u77e5\u89c2\u5bdf\u8005\u8bf7\u6c42\u6807\u8bc6\u53ca\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u662f\u5426\u6e05\u7406\u7684\u56de\u8c03\u3002 */
    private final BiConsumer<String, Boolean> completed;

    /**
     * \u88c5\u914d\u9a8c\u8bc1\u8bf7\u6c42\u7ebf\u7a0b\u7684\u539f\u751f\u8ba4\u8bc1\u8fc7\u6ee4\u5668\u3002
     * @param auth \u5f53\u524d\u5e94\u7528\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6
     * @param completed \u89c2\u5bdf\u8bf7\u6c42\u53ca\u4e0a\u4e0b\u6587\u6e05\u7406\u7ed3\u679c\u7684\u56de\u8c03
     */
    NativeRequestFilter(Supplier<NativeAuth> auth, BiConsumer<String, Boolean> completed) {
        this.auth = auth;
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
