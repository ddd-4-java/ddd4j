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
    /** \u968f\u5e94\u7528\u5173\u95ed\u4e00\u540c\u56de\u6536\u7684 SPI \u4ed3\u50a8\u6ce8\u518c\u4f5c\u7528\u57df\u3002 */
    final SpiRegistrationScope repositories = new SpiRegistrationScope();
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u63d0\u4f9b\u5668\u3002 */
    private final SubjectProvider previousProvider = SubjectKit.subjectProvider;
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u6570\u636e\u63d0\u4f9b\u5668\u3002 */
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u7b56\u7565\u3002 */
    private final SubjectStrategy previousStrategy = SubjectKit.strategy;
    /** \u521d\u59cb\u5316\u524d\u6ce8\u518c\u7684 Guice \u6ce8\u5165\u5668\u3002 */
    private final Injector previousInjector = GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    /** \u7528\u4e8e\u62a5\u544a\u8bf7\u6c42\u4e3b\u4f53\u6216\u6846\u67b6\u4e0a\u4e0b\u6587\u6b8b\u7559\u7684\u89c2\u5bdf\u56de\u8c03\u3002 */
    private final Consumer<Optional<String>> observer;
    /** \u521d\u59cb\u5316\u524d\u7684 Sa-Token \u4e0a\u4e0b\u6587\u3002 */
    private final SaTokenContext previousContext = SaManager.getSaTokenContext();
    /** \u521d\u59cb\u5316\u524d\u7684 Sa-Token \u914d\u7f6e\u3002 */
    private final SaTokenConfig previousConfig = SaManager.getConfig();
    /** \u521d\u59cb\u5316\u524d\u7684 Sa-Token \u6743\u9650\u63d0\u4f9b\u5668\u3002 */
    private final StpInterface previousPermissions = SaManager.getStpInterface();
    /** \u7531\u5f53\u524d\u793a\u4f8b\u5b89\u88c5\u7684 Sa-Token \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final SaTokenContext context = new SaTokenContextForThreadLocal();
    /** \u7531\u5f53\u524d\u793a\u4f8b\u5b89\u88c5\u7684 Sa-Token \u914d\u7f6e\u3002 */
    private final SaTokenConfig config = new SaTokenConfig().setTokenName("ddd4j-token").setIsPrint(false).setDataRefreshPeriod(-1);
    /** \u672c\u5b9e\u4f8b\u5b89\u88c5\u7684 Sa-Token \u6743\u9650\u63d0\u4f9b\u5668\u3002 */
    private final StpInterface permissions = new SaTokenSubjectDataBridge();
    /** \u5f53\u524d\u5e94\u7528\u6ce8\u518c\u7684 DDD4J \u4e3b\u4f53\u63d0\u4f9b\u5668\u3002 */
    private SubjectProvider provider;
    /** \u672c\u5b9e\u4f8b\u901a\u8fc7\u4f9d\u8d56\u6ce8\u5165\u83b7\u53d6\u7684\u4e3b\u4f53\u6570\u636e\u63d0\u4f9b\u5668\u3002 */
    private SubjectDataProvider data;
    /** \u672c\u5b9e\u4f8b\u7ed1\u5b9a\u7684 Guice \u6ce8\u5165\u5668\u3002 */
    private Injector injector;
    /** \u5f53\u524d\u5b9e\u4f8b\u7ba1\u7406\u7684 Javalin \u5e94\u7528\u3002 */
    private Javalin app;
    /** \u672c\u5b9e\u4f8b\u5df2\u7ecf\u6267\u884c\u5173\u95ed\u7684\u6807\u8bb0\u3002 */
    private boolean closed;
    /** \u672c\u5b9e\u4f8b\u62e5\u6709\u4e14\u8d1f\u8d23\u89e3\u9664\u6ce8\u518c\u7684 Sa-Token \u4f1a\u8bdd\u5b58\u50a8\u3002 */
    private SaTokenDao ownedDao;

    /**
     * \u5efa\u7acb\u539f\u751f\u8ba4\u8bc1\u8fd0\u884c\u65f6\u7684\u8d44\u6e90\u6240\u6709\u6743\u53ca\u4e0a\u4e0b\u6587\u89c2\u5bdf\u56de\u8c03\u3002
     * @param observer \u63a5\u6536\u8bf7\u6c42\u4e3b\u4f53\u6216\u6846\u67b6\u4e0a\u4e0b\u6587\u6b8b\u7559\u4fe1\u53f7\u7684\u89c2\u5bdf\u56de\u8c03
     */
    NativeAuthLifecycle(Consumer<Optional<String>> observer) {
        this.observer = observer;
    }

    /**
     * getter \u4f1a\u521b\u5efa\u5e76\u542f\u52a8\u9ed8\u8ba4 DAO\uff1b\u53ea\u8bfb\u5feb\u7167\u4fdd\u7559\u539f\u672c\u672a\u521d\u59cb\u5316\u7684\u72b6\u6001\u3002
     *
     * @return \u5f53\u524d\u767b\u8bb0\u7684\u4f1a\u8bdd\u6570\u636e\u8bbf\u95ee\u7ec4\u4ef6\uff0c\u672a\u767b\u8bb0\u65f6\u4e3a\u7a7a
     */
    private static SaTokenDao currentDao() {
        var field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"),
                "Sa-Token 1.45.0 nullable saTokenDao field is required for lifecycle ownership");
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(field);
    }

    /** \u5b89\u88c5\u793a\u4f8b\u7684 Sa-Token \u914d\u7f6e\u3001\u4e0a\u4e0b\u6587\u53ca\u4f1a\u8bdd\u7ec4\u4ef6\u3002 */
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
     * \u4ece Guice \u83b7\u53d6\u5e76\u6ce8\u518c\u5f53\u524d\u5e94\u7528\u7684 DDD4J \u4e3b\u4f53\u7ec4\u4ef6\u3002
     * @param injector \u63d0\u4f9b\u8fd0\u884c\u65f6\u53ca\u8ba4\u8bc1\u7ec4\u4ef6\u7684 Guice \u6ce8\u5165\u5668
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
     * \u5728\u4e1a\u52a1\u5904\u7406\u524d\u68c0\u67e5\u8ba2\u5355\u767b\u5f55\u72b6\u6001\uff0c\u6cbf\u7528\u672a\u5339\u914d URL \u7684\u65e7 Servlet \u54cd\u5e94\u3002
     *
     * @param ctx \u5f53\u524d Javalin HTTP \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @throws IOException \u5199\u51fa\u8ba4\u8bc1\u5931\u8d25\u7684 Servlet \u54cd\u5e94\u65f6\u51fa\u9519
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
     * \u5728 Javalin \u786e\u5b9a HTTP \u8def\u7531\u540e\u9009\u62e9\u6743\u9650\uff0c\u4fdd\u7559\u8be5\u8def\u7531\u652f\u6301\u7684\u5c3e\u659c\u6760\u8bed\u4e49\u3002
     *
     * @param ctx \u5305\u542b\u5339\u914d\u8def\u7531\u7684 Javalin \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @throws IOException \u8bbf\u95ee\u6216\u5199\u51fa Servlet \u54cd\u5e94\u65f6\u51fa\u9519
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
     * \u68c0\u67e5\u5f53\u524d\u7ebf\u7a0b\u662f\u5426\u8fd8\u4fdd\u7559\u6846\u67b6\u4e0a\u4e0b\u6587\u6216\u539f\u751f\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @return \u4e0a\u4e0b\u6587\u6b8b\u7559\u6807\u8bb0\uff0c\u7ebf\u7a0b\u5e72\u51c0\u65f6\u4e3a\u7a7a
     */
    private Optional<String> observe() {
        if (!io.ddd4j.core.context.ThreadContext.getResources().isEmpty()) {
            return Optional.of("framework-context-present");
        }
        return context.isValid() ? Optional.of("native-context-present") : Optional.empty();
    }

    /** \u5173\u95ed\u672c\u5b9e\u4f8b\u62e5\u6709\u7684\u8ba4\u8bc1\u548c\u5e94\u7528\u8d44\u6e90\uff0c\u6062\u590d\u539f\u5148\u7684\u5168\u5c40\u4e0e\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u3002 */
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

    /** \u6062\u590d\u672c\u5b9e\u4f8b\u5b89\u88c5\u7684 Sa-Token \u7ec4\u4ef6\u53ca DDD4J \u5171\u4eab\u6ce8\u518c\u3002 */
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

    /** \u4ec5\u6062\u590d\u5f53\u524d\u6ce8\u518c\u4ecd\u5c5e\u4e8e\u672c\u5b9e\u4f8b\u7684 DDD4J \u4e3b\u4f53\u7ec4\u4ef6\u548c Guice \u6ce8\u5165\u5668\u3002 */
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
