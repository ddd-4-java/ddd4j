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
    final SpiRegistrationScope repositories = new SpiRegistrationScope();
    private final SubjectProvider previousProvider = SubjectKit.subjectProvider;
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    private final SubjectStrategy previousStrategy = SubjectKit.strategy;
    private final Injector previousInjector = GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    private final Consumer<Optional<String>> observer;
    private final SaTokenContext previousContext = SaManager.getSaTokenContext();
    private final SaTokenConfig previousConfig = SaManager.getConfig();
    private final StpInterface previousPermissions = SaManager.getStpInterface();
    private final SaTokenContext context = new SaTokenContextForThreadLocal();
    private final SaTokenConfig config = new SaTokenConfig().setTokenName("ddd4j-token").setIsPrint(false).setDataRefreshPeriod(-1);
    private final StpInterface permissions = new SaTokenSubjectDataBridge();
    private SubjectProvider provider;
    private SubjectDataProvider data;
    private Injector injector;
    private Javalin app;
    private boolean closed;
    private SaTokenDao ownedDao;

    NativeAuthLifecycle(Consumer<Optional<String>> observer) {
        this.observer = observer;
    }

    /**
     * getter 会创建并启动默认 DAO；只读快照保留原本未初始化的状态。
     */
    private static SaTokenDao currentDao() {
        var field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"),
                "Sa-Token 1.45.0 nullable saTokenDao field is required for lifecycle ownership");
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(field);
    }

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

    void attach(Injector injector) {
        this.injector = injector;
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
     */
    void authenticateOrder(Context ctx) throws IOException {
        if (ctx.req().getRequestURI().startsWith("/orders") && !StpUtil.isLogin()) {
            var endpoint = ctx.endpoints().matchedHttpEndpoint();
            if (Objects.nonNull(endpoint) && endpoint.path.startsWith("/orders")) {
                ctx.status(401).json(R.fail(401, "not login"));
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
            if (!StpUtil.hasPermission(permission)) {
                ctx.status(403).json(R.fail(403, "required permission is missing"));
                ctx.skipRemainingHandlers();
            }
        }
    }

    private Optional<String> observe() {
        if (!io.ddd4j.core.context.ThreadContext.getResources().isEmpty()) {
            return Optional.of("framework-context-present");
        }
        return context.isValid() ? Optional.of("native-context-present") : Optional.empty();
    }

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
