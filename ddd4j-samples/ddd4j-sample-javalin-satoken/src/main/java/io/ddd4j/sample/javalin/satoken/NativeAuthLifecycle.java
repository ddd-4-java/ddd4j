package io.ddd4j.sample.javalin.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ReflectUtil;
import com.google.inject.Injector;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectDataBridge;
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
    private final Injector previousInjector = GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    /**
     * 用于报告请求主体或框架上下文残留的观察回调。
     */
    private final Consumer<Optional<String>> observer;
    /**
     * 初始化前的 Sa-Token 上下文。
     */
    private final SaTokenContext previousContext = SaManager.getSaTokenContext();
    /**
     * 初始化前的 Sa-Token 配置。
     */
    private final SaTokenConfig previousConfig = SaManager.getConfig();
    /**
     * 初始化前的 Sa-Token 权限提供器。
     */
    private final StpInterface previousPermissions = SaManager.getStpInterface();
    /**
     * 由当前示例安装的 Sa-Token 请求上下文。
     */
    private final SaTokenContext context = new SaTokenContextForThreadLocal();
    /**
     * 由当前示例安装的 Sa-Token 配置。
     */
    private final SaTokenConfig config = new SaTokenConfig().setTokenName("ddd4j-token").setIsPrint(false).setDataRefreshPeriod(-1);
    /**
     * 本实例安装的 Sa-Token 权限提供器。
     */
    private final StpInterface permissions = new SaTokenSubjectDataBridge();
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
     * 当前实例管理的 Javalin 应用。
     */
    private Javalin app;
    /**
     * 本实例已经执行关闭的标记。
     */
    private boolean closed;
    /**
     * 本实例拥有且负责解除注册的 Sa-Token 会话存储。
     */
    private SaTokenDao ownedDao;

    /**
     * 建立原生认证运行时的资源所有权及上下文观察回调。
     *
     * @param observer 接收请求主体或框架上下文残留信号的观察回调
     */
    NativeAuthLifecycle(Consumer<Optional<String>> observer) {
        this.observer = observer;
    }

    /**
     * getter 会创建并启动默认 DAO；只读快照保留原本未初始化的状态。
     *
     * @return 当前登记的会话数据访问组件，未登记时为空
     */
    private static SaTokenDao currentDao() {
        var field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"),
                "Sa-Token 1.45.0 nullable saTokenDao field is required for lifecycle ownership");
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(field);
    }

    /**
     * 安装示例的 Sa-Token 配置、上下文及会话组件。
     */
    void initializeSaToken() {
        SaManager.setSaTokenContext(context);
        SaManager.setConfig(config);
        // 沿用调用方注册的存储。Sa-Token 替换存储时会销毁前一个实例。
        if (Objects.isNull(currentDao())) {
            ownedDao = new SaTokenDaoDefaultImpl();
            SaManager.setSaTokenDao(ownedDao);
        }
        SaManager.setStpInterface(permissions);
        observer.accept(observe());
    }

    /**
     * 从 Guice 获取并注册当前应用的 DDD4J 主体组件。
     *
     * @param injector 提供运行时及认证组件的 Guice 注入器
     */
    void attach(Injector injector) {
        this.injector = injector;
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
        var previous = context.isValid() ? context.getModelBox() : null;
        // Javalin 直接使用 Servlet 适配器，进程级工厂可由其他 Sa-Token 集成持有。
        context.setContext(new SaRequestForServlet(request), new SaResponseForServlet(response),
                new SaStorageForServlet(request));
        var frameworkScope = io.ddd4j.core.context.ThreadContext.open();
        try {
            io.ddd4j.core.context.ThreadContext.bind(provider.getSubject());
            String path = request.getRequestURI();
            if (path.equals("/auth/me") || path.equals("/auth/logout")) {
                if (!StpUtil.isLogin()) {
                    response.sendError(401);
                    return;
                }

            }
            chain.doFilter(request, response);
        } finally {
            frameworkScope.close();
            context.clearContext();
            if (Objects.nonNull(previous)) {
                context.setContext(previous.getRequest(), previous.getResponse(), previous.getStorage());
            }
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
        if (ctx.req().getRequestURI().startsWith("/orders") && !StpUtil.isLogin()) {
            var endpoint = ctx.endpoints().matchedHttpEndpoint();
            if (Objects.nonNull(endpoint) && endpoint.path.startsWith("/orders")) {
                ctx.status(401).json(R.of(401, "not login"));
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
            if (!StpUtil.hasPermission(permission)) {
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
        return context.isValid() ? Optional.of("native-context-present") : Optional.empty();
    }

    /**
     * 关闭本实例拥有的认证和应用资源，恢复原先的全局与线程上下文。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (var callerContext = io.ddd4j.core.context.ThreadContext.open()) {
            try {
                if (Objects.nonNull(app)) {
                    app.stop();
                }
            } finally {
                try {
                    repositories.close();
                } finally {
                    try {
                        if (Objects.nonNull(injector)) {
                            injector.getInstance(Ddd4jGuiceRuntime.class).close();
                        }
                    } finally {
                        restore();
                    }
                }
            }
        }
    }

    /**
     * 恢复本实例安装的 Sa-Token 组件及 DDD4J 共享注册。
     */
    private void restore() {
        Throwable restorationFailure = null;
        try {
            restoreSharedComponents();
        } catch (RuntimeException | Error failure) {
            restorationFailure = failure;
            throw failure;
        } finally {
            // SDK 的组件注册监听器可能抛出异常，仍须释放本应用拥有的 DAO。
            try {
                if (Objects.nonNull(ownedDao) && currentDao() == ownedDao) {
                    SaManager.setSaTokenDao(null);
                }
            } catch (RuntimeException | Error cleanupFailure) {
                if (Objects.isNull(restorationFailure)) {
                    throw cleanupFailure;
                }
                if (cleanupFailure != restorationFailure) {
                    restorationFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    /**
     * 仅恢复当前注册仍属于本实例的 DDD4J 主体组件和 Guice 注入器。
     */
    private void restoreSharedComponents() {
        if (Objects.nonNull(previousInjector)) {
            GuiceContext.setInjector(previousInjector);
        }
        if (SubjectKit.subjectProvider == provider) {
            SubjectKit.subjectProvider = previousProvider;
        }
        if (SubjectKit.dataProvider == data) {
            SubjectKit.dataProvider = previousData;
        }
        if (SubjectKit.strategy == SubjectStrategy.instance) {
            SubjectKit.strategy = previousStrategy;
        }
        if (SaManager.getSaTokenContext() == context) {
            SaManager.setSaTokenContext(previousContext);
        }
        if (SaManager.getConfig() == config) {
            SaManager.setConfig(previousConfig);
        }
        if (SaManager.getStpInterface() == permissions) {
            SaManager.setStpInterface(previousPermissions);
        }
    }
}
