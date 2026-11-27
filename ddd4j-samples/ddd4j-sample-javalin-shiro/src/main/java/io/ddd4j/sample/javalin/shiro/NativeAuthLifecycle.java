package io.ddd4j.sample.javalin.shiro;

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
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    final SpiRegistrationScope repositories = new SpiRegistrationScope();
    private final SubjectProvider previousProvider = SubjectKit.subjectProvider;
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    private final SubjectStrategy previousStrategy = SubjectKit.strategy;
    private final Injector previousInjector = currentInjector();
    private final Consumer<Optional<String>> observer;
    private final DefaultSecurityManager securityManager;
    private SubjectProvider provider;
    private SubjectDataProvider data;
    private Injector injector;
    private Ddd4jGuiceRuntime runtime;
    private Javalin app;
    private boolean closed;

    NativeAuthLifecycle(DefaultSecurityManager securityManager, Consumer<Optional<String>> observer) {
        this.observer = observer;
        this.securityManager = securityManager;
        observer.accept(observe());
    }

    private static Injector currentInjector() {
        return GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    }

    void attach(Injector injector) {
        this.injector = injector;
        runtime = injector.getInstance(Ddd4jGuiceRuntime.class);
        provider = injector.getInstance(SubjectProvider.class);
        data = injector.getInstance(SubjectDataProvider.class);
        SubjectKit.register(provider);
        SubjectKit.setDataProvider(data);
        SubjectKit.setStrategy(SubjectStrategy.instance);
    }

    void attach(Javalin app) {
        this.app = app;
    }

    Javalin app() {
        return app;
    }

    @Override
    public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        var previous = ThreadContext.getResources();
        try (var frameworkScope = io.ddd4j.core.context.ThreadContext.open()) {
            String token = request.getHeader("ddd4j-token");
            Subject.Builder builder = new Subject.Builder(securityManager);
            if (Objects.nonNull(token) && !token.isBlank()) {
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
     * 在业务处理前检查订单登录状态，沿用未匹配 URL 的旧 Servlet 响应。
     */
    void authenticateOrder(Context ctx) throws IOException {
        if (ctx.req().getRequestURI().startsWith("/orders") && !org.apache.shiro.SecurityUtils.getSubject().isAuthenticated()) {
            var endpoint = ctx.endpoints().matchedHttpEndpoint();
            if (Objects.nonNull(endpoint) && endpoint.path.startsWith("/orders")) {
                ctx.status(401).json(R.of(401, "unauthenticated"));
            } else {
                ctx.res().sendError(401);
            }
            ctx.skipRemainingHandlers();
        }
    }

    /**
     * 在 Javalin 确定 HTTP 路由后选择权限，保留该路由支持的尾斜杠语义。
     */
    void authorizeMatchedOrder(Context ctx) throws IOException {
        var endpoint = ctx.endpoints().matchedHttpEndpoint();
        if (Objects.nonNull(endpoint) && endpoint.path.startsWith("/orders")) {
            if (endpoint.path.startsWith("/orders/catalog/") && "GET".equals(ctx.method().name())) {
                return;
            }
            String permission = "/orders/catalog/orders/{orderNo}/cancel".equals(endpoint.path) ? "order:cancel" : "order:pay";
            if (!org.apache.shiro.SecurityUtils.getSubject().isPermitted(permission)) {
                ctx.status(403).json(R.of(403, "required permission is missing"));
                ctx.skipRemainingHandlers();
            }
        }
    }

    private Optional<String> observe() {
        if (!io.ddd4j.core.context.ThreadContext.getResources().isEmpty()) {
            return Optional.of("framework-context-present");
        }
        return Optional.ofNullable(ThreadContext.getSubject()).map(subject -> "native-subject-present");
    }

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
