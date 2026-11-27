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
    /**
     * 随应用关闭一同回收的 SPI 仓储注册作用域。
     */
    final SpiRegistrationScope repositories = new SpiRegistrationScope();
    /**
     * 初始化前注册的 DDD4J 主体提供器。
     */
    private final SubjectProvider previousProvider = SubjectKit.subjectProvider;
    /**
     * 初始化前注册的 DDD4J 主体数据提供器。
     */
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    /**
     * 初始化前注册的 DDD4J 主体策略。
     */
    private final SubjectStrategy previousStrategy = SubjectKit.strategy;
    /**
     * 初始化前注册的 Guice 注入器。
     */
    private final Injector previousInjector = currentInjector();
    /**
     * 用于报告请求主体或框架上下文残留的观察回调。
     */
    private final Consumer<Optional<String>> observer;
    /**
     * 用于解析当前请求的 Shiro 安全管理器。
     */
    private final DefaultSecurityManager securityManager;
    /**
     * 当前应用注册的 DDD4J 主体提供器。
     */
    private SubjectProvider provider;
    /**
     * 本实例通过依赖注入获取的主体数据提供器。
     */
    private SubjectDataProvider data;
    /**
     * 本实例绑定的 Guice 注入器。
     */
    private Injector injector;
    /**
     * 当前应用管理的 DDD4J Guice 运行时。
     */
    private Ddd4jGuiceRuntime runtime;
    /**
     * 当前实例管理的 Javalin 应用。
     */
    private Javalin app;
    /**
     * 本实例已经执行关闭的标记。
     */
    private boolean closed;

    /**
     * 建立原生认证运行时的资源所有权及上下文观察回调。
     *
     * @param securityManager 当前应用的 Shiro 安全管理器
     * @param observer        接收请求主体或框架上下文残留信号的观察回调
     */
    NativeAuthLifecycle(DefaultSecurityManager securityManager, Consumer<Optional<String>> observer) {
        this.observer = observer;
        this.securityManager = securityManager;
        observer.accept(observe());
    }

    /**
     * 读取 Guice 当前已注册的依赖注入器。
     *
     * @return 当前注入器，Guice 上下文未初始化时为空
     */
    private static Injector currentInjector() {
        return GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    }

    /**
     * 从 Guice 获取并注册当前应用的 DDD4J 主体组件。
     *
     * @param injector 提供运行时及认证组件的 Guice 注入器
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
     * 绑定本实例管理的 Javalin 应用。
     *
     * @param app 本实例负责关闭的 Javalin 应用
     */
    void attach(Javalin app) {
        this.app = app;
    }

    /**
     * 读取本实例当前持有的 Javalin 应用。
     *
     * @return 当前的 Javalin 应用
     */
    Javalin app() {
        return app;
    }

    /**
     * 为请求绑定原生认证主体与 DDD4J 上下文，并在处理结束后恢复调用者绑定。
     *
     * @param rawRequest  进入过滤器的 Servlet 请求
     * @param rawResponse 传给过滤器链的 Servlet 响应
     * @param chain       进入业务处理的 Servlet 过滤器链
     * @throws IOException      读取请求或输出响应时发生 I/O 错误
     * @throws ServletException Servlet 过滤器链处理失败
     */
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
     *
     * @param ctx 当前 Javalin HTTP 请求上下文
     * @throws IOException 写出认证失败的 Servlet 响应时出错
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
     *
     * @param ctx 包含匹配路由的 Javalin 请求上下文
     * @throws IOException 访问或写出 Servlet 响应时出错
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

    /**
     * 检查当前线程是否还保留框架上下文或原生认证主体。
     *
     * @return 上下文残留标记，线程干净时为空
     */
    private Optional<String> observe() {
        if (!io.ddd4j.core.context.ThreadContext.getResources().isEmpty()) {
            return Optional.of("framework-context-present");
        }
        return Optional.ofNullable(ThreadContext.getSubject()).map(subject -> "native-subject-present");
    }

    /**
     * 关闭本实例拥有的认证和应用资源，恢复原先的全局与线程上下文。
     */
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
