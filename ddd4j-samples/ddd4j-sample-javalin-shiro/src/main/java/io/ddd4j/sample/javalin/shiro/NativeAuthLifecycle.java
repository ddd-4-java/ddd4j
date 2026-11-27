package io.ddd4j.sample.javalin.shiro;

import io.ddd4j.kit.lang.StrKit;
import com.google.inject.Injector;
import io.ddd4j.core.api.R;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.guice.Ddd4jGuiceRuntime;
import io.ddd4j.guice.context.GuiceContext;
import io.javalin.Javalin;
import io.javalin.http.Context;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 原生认证请求作用域及本实例资源所有权；观察钩子只读，不参与清理。
 */
final class NativeAuthLifecycle implements Filter, AutoCloseable {
    /** \u968f\u5e94\u7528\u5173\u95ed\u4e00\u540c\u56de\u6536\u7684 SPI \u4ed3\u50a8\u6ce8\u518c\u4f5c\u7528\u57df\u3002 */
    final SpiRegistrationScope repositories = new SpiRegistrationScope();
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u63d0\u4f9b\u5668\u3002 */
    private final SubjectProvider previousProvider = SubjectKit.subjectProvider;
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u6570\u636e\u63d0\u4f9b\u5668\u3002 */
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u7b56\u7565\u3002 */
    private final SubjectStrategy previousStrategy = SubjectKit.strategy;
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 Guice \u6ce8\u5165\u5668\u3002 */
    private final Injector previousInjector = currentInjector();
    /** \u7528\u4e8e\u62a5\u544a\u8bf7\u6c42\u4e3b\u4f53\u6216\u6846\u67b6\u4e0a\u4e0b\u6587\u6b8b\u7559\u7684\u89c2\u5bdf\u56de\u8c03\u3002 */
    private final Consumer<Optional<String>> observer;
    /** \u7528\u4e8e\u89e3\u6790\u5f53\u524d\u8bf7\u6c42\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668\u3002 */
    private final DefaultSecurityManager securityManager;
    /** \u5f53\u524d\u5e94\u7528\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u63d0\u4f9b\u5668\u3002 */
    private SubjectProvider provider;
    /** \u672c\u5b9e\u4f8b\u901a\u8fc7\u4f9d\u8d56\u6ce8\u5165\u83b7\u53d6\u7684\u4e3b\u4f53\u6570\u636e\u63d0\u4f9b\u5668\u3002 */
    private SubjectDataProvider data;
    /** \u672c\u5b9e\u4f8b\u7ed1\u5b9a\u7684 Guice \u6ce8\u5165\u5668\u3002 */
    private Injector injector;
    /** \u5f53\u524d\u5e94\u7528\u7ba1\u7406\u7684 DDD4J Guice \u8fd0\u884c\u65f6\u3002 */
    private Ddd4jGuiceRuntime runtime;
    /** \u5f53\u524d\u5b9e\u4f8b\u7ba1\u7406\u7684 Javalin \u5e94\u7528\u3002 */
    private Javalin app;
    /** \u672c\u5b9e\u4f8b\u5df2\u7ecf\u6267\u884c\u5173\u95ed\u7684\u6807\u8bb0\u3002 */
    private boolean closed;

    /**
     * \u5efa\u7acb\u539f\u751f\u8ba4\u8bc1\u8fd0\u884c\u65f6\u7684\u8d44\u6e90\u6240\u6709\u6743\u53ca\u4e0a\u4e0b\u6587\u89c2\u5bdf\u56de\u8c03\u3002
     * @param securityManager \u5f53\u524d\u5e94\u7528\u7684 Shiro \u5b89\u5168\u7ba1\u7406\u5668
     * @param observer \u63a5\u6536\u8bf7\u6c42\u4e3b\u4f53\u6216\u6846\u67b6\u4e0a\u4e0b\u6587\u6b8b\u7559\u4fe1\u53f7\u7684\u89c2\u5bdf\u56de\u8c03
     */
    NativeAuthLifecycle(DefaultSecurityManager securityManager, Consumer<Optional<String>> observer) {
        this.observer = observer;
        this.securityManager = securityManager;
        observer.accept(observe());
    }

    /**
     * \u8bfb\u53d6 Guice \u5f53\u524d\u5df2\u6ce8\u518c\u7684\u4f9d\u8d56\u6ce8\u5165\u5668\u3002
     * @return \u5f53\u524d\u6ce8\u5165\u5668\uff0cGuice \u4e0a\u4e0b\u6587\u672a\u521d\u59cb\u5316\u65f6\u4e3a\u7a7a
     */
    private static Injector currentInjector() {
        return GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    }

    /**
     * \u4ece Guice \u83b7\u53d6\u5e76\u6ce8\u518c\u5f53\u524d\u5e94\u7528\u7684 DDD4J \u4e3b\u4f53\u7ec4\u4ef6\u3002
     * @param injector \u63d0\u4f9b\u8fd0\u884c\u65f6\u53ca\u8ba4\u8bc1\u7ec4\u4ef6\u7684 Guice \u6ce8\u5165\u5668
     */
    void attach(Injector injector) {
        this.injector = injector;
        runtime = injector.getInstance(Ddd4jGuiceRuntime.class);
        provider = injector.getInstance(SubjectProvider.class);
        data = injector.getInstance(SubjectDataProvider.class);
        SubjectKit.register(provider);
        SubjectKit.setDataProvider(data);
        SubjectKit.setStrategy(SubjectStrategy.instance);
    }

    /**
     * \u7ed1\u5b9a\u672c\u5b9e\u4f8b\u7ba1\u7406\u7684 Javalin \u5e94\u7528\u3002
     * @param app \u672c\u5b9e\u4f8b\u8d1f\u8d23\u5173\u95ed\u7684 Javalin \u5e94\u7528
     */
    void attach(Javalin app) {
        this.app = app;
    }

    /**
     * \u8bfb\u53d6\u672c\u5b9e\u4f8b\u5f53\u524d\u6301\u6709\u7684 Javalin \u5e94\u7528\u3002
     * @return \u5f53\u524d\u7684 Javalin \u5e94\u7528
     */
    Javalin app() {
        return app;
    }

    /**
     * \u4e3a\u8bf7\u6c42\u7ed1\u5b9a\u539f\u751f\u8ba4\u8bc1\u4e3b\u4f53\u4e0e DDD4J \u4e0a\u4e0b\u6587\uff0c\u5e76\u5728\u5904\u7406\u7ed3\u675f\u540e\u6062\u590d\u8c03\u7528\u8005\u7ed1\u5b9a\u3002
     * @param rawRequest \u8fdb\u5165\u8fc7\u6ee4\u5668\u7684 Servlet \u8bf7\u6c42
     * @param rawResponse \u4f20\u7ed9\u8fc7\u6ee4\u5668\u94fe\u7684 Servlet \u54cd\u5e94
     * @param chain \u8fdb\u5165\u4e1a\u52a1\u5904\u7406\u7684 Servlet \u8fc7\u6ee4\u5668\u94fe
     * @throws IOException \u8bfb\u53d6\u8bf7\u6c42\u6216\u8f93\u51fa\u54cd\u5e94\u65f6\u53d1\u751f I/O \u9519\u8bef
     * @throws ServletException Servlet \u8fc7\u6ee4\u5668\u94fe\u5904\u7406\u5931\u8d25
     */
    @Override
    public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        java.util.Map<Object, Object> previous = ThreadContext.getResources();
        try (io.ddd4j.core.context.ThreadContext.Scope frameworkScope = io.ddd4j.core.context.ThreadContext.open()) {
            String token = request.getHeader("ddd4j-token");
            Subject.Builder builder = new Subject.Builder(securityManager);
            if (Objects.nonNull(token) && !StrKit.isBlank(token)) {
                builder.sessionId(token);
            }
            ThreadContext.bind(builder.buildSubject());
            io.ddd4j.core.context.ThreadContext.bind(provider.getSubject());
            String path = request.getRequestURI();
            if (path.equals("/auth/me") || path.equals("/auth/logout")) {
                if (!org.apache.shiro.SecurityUtils.getSubject().isAuthenticated()) {
                    response.sendError(401);
                    return;
                }

            }
            chain.doFilter(request, response);
        } finally {
            ThreadContext.remove();
            ThreadContext.setResources(previous);
            observer.accept(observe());
        }
    }

    /**
     * 执行 <code>authorize Order</code>，调用 <code>SecurityUtils.getSubject</code>、<code>ctx.status</code>、<code>R.of</code>。
     *
     * @param ctx 传入此操作的 <code>Context</code> 类型 <code>ctx</code>
     * @param permission 传入此操作的 <code>String</code> 类型 <code>permission</code>
     * @return 按条件返回 <code>false</code>、<code>true</code>、<code>false</code>
     */
    boolean authorizeOrder(Context ctx, String permission) {
        if (!org.apache.shiro.SecurityUtils.getSubject().isAuthenticated()) {
            ctx.status(401).json(R.of(401, "unauthenticated"));
            return false;
        }
        if ("order:read".equals(permission)) {
            return true;
        }
        if (!org.apache.shiro.SecurityUtils.getSubject().isPermitted(permission)) {
            ctx.status(403).json(R.of(403, "required permission is missing"));
            return false;
        }
        return true;
    }

    /**
     * \u68c0\u67e5\u5f53\u524d\u7ebf\u7a0b\u662f\u5426\u8fd8\u4fdd\u7559\u6846\u67b6\u4e0a\u4e0b\u6587\u6216\u539f\u751f\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @return \u4e0a\u4e0b\u6587\u6b8b\u7559\u6807\u8bb0\uff0c\u7ebf\u7a0b\u5e72\u51c0\u65f6\u4e3a\u7a7a
     */
    private Optional<String> observe() {
        if (!io.ddd4j.core.context.ThreadContext.getResources().isEmpty()) {
            return Optional.of("framework-context-present");
        }
        return Optional.ofNullable(ThreadContext.getSubject()).map(subject -> "native-subject-present");
    }

    /** \u5173\u95ed\u672c\u5b9e\u4f8b\u62e5\u6709\u7684\u8ba4\u8bc1\u548c\u5e94\u7528\u8d44\u6e90\uff0c\u6062\u590d\u539f\u5148\u7684\u5168\u5c40\u4e0e\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u3002 */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (io.ddd4j.core.context.ThreadContext.Scope caller = io.ddd4j.core.context.ThreadContext.open()) {
            Injector activeInjector = currentInjector();
            try (Ddd4jGuiceRuntime ownedRuntime = runtime; SpiRegistrationScope ownedRepositories = repositories) {
                if (Objects.nonNull(app)) {
                    app.stop();
                }
            } finally {
                if (Objects.nonNull(runtime)) {
                    Injector restore = activeInjector == injector ? previousInjector : activeInjector;
                    if (Objects.nonNull(restore)) {
                        GuiceContext.setInjector(restore);
                    }
                }
                if (Objects.nonNull(provider) && SubjectKit.subjectProvider == provider) {
                    SubjectKit.subjectProvider = previousProvider;
                    if (SubjectKit.strategy == SubjectStrategy.instance) {
                        SubjectKit.strategy = previousStrategy;
                    }
                }
                if (Objects.nonNull(data) && SubjectKit.dataProvider == data) {
                    SubjectKit.dataProvider = previousData;
                }
                securityManager.destroy();
            }
        }
    }
}
