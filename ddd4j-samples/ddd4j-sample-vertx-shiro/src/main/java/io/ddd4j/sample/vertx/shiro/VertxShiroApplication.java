package io.ddd4j.sample.vertx.shiro;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.adapter.http.Reply;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.vertx.Ddd4jVertxContext;
import io.ddd4j.web.vertx.Ddd4jVertxWeb;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 原生 Shiro 商品交易示例。
 */
@Slf4j
public final class VertxShiroApplication {
    /**
     * 为当前示例提供 HTTP 服务及工作线程的 Vert.x 实例。
     */
    private final Vertx vertx = Vertx.vertx();
    /**
     * 当前示例已装配的 Vert.x DDD4J 运行时。
     */
    private final Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.create(vertx, List.of());
    /**
     * 解析请求认证主体的提供器或管理其注册生命周期的作用域。
     */
    private final io.ddd4j.core.util.SubjectKitRegistrationScope subjects = new io.ddd4j.core.util.SubjectKitRegistrationScope(
            new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider());
    /**
     * 当前 HTTP 示例使用的共用商品交易入口。
     */
    private final CatalogEndpoints catalog;
    /**
     * 在请求处理后通知测试或外部观察者的回调。
     */
    private final BiConsumer<String, Boolean> completed;
    /**
     * 已经提交且尚未完成的请求处理任务。
     */
    private final Set<Future<?>> inFlight = ConcurrentHashMap.newKeySet();
    /**
     * 处理当前示例应用登录状态的原生认证组件。
     */
    private NativeAuth auth;
    /**
     * 由当前应用持有并在停止时关闭的 Vert.x HTTP 服务器。
     */
    private HttpServer server;
    /**
     * 标记当前示例是否已经开始关闭。
     */
    private volatile boolean closing;
    /**
     * 用于观察异步停止及资源回收结果的完成信号。
     */
    private CompletableFuture<Void> closed;

    /**
     * 保存商品交易入口及流程结束时使用的观察回调。
     *
     * @param catalog   当前商品交易入口
     * @param completed 请求处理完成时调用的观察回调或完成信号
     */
    private VertxShiroApplication(CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        this.catalog = catalog;
        this.completed = completed;
    }

    /**
     * @param port 监听端口
     * @return 已启动原生应用
     */
    public static Future<VertxShiroApplication> start(int port) {
        return start(port, CatalogRuntime.start(), (id, clean) -> {
        });
    }

    // 所有入口共用路由；包内观测器只观察工作线程恢复后的结果，不修改上下文。
    /**
     * 启动包含商品交易路由的 Vert.x HTTP 服务。
     *
     * @param port      当前 HTTP 示例要监听的端口
     * @param catalog   当前商品交易入口
     * @param completed 请求处理完成时调用的观察回调或完成信号
     * @return 应用启动后的运行实例或其异步结果
     */
    static Future<VertxShiroApplication> start(int port, CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        var app = new VertxShiroApplication(catalog, completed);
        var promise = io.vertx.core.Promise.<VertxShiroApplication>promise();
        try {
            app.runtime.start();
            app.auth = new NativeAuth();
            app.subjects.start();
            app.vertx.createHttpServer().requestHandler(app.router()).listen(port, "127.0.0.1").onComplete(result -> {
                if (result.succeeded()) {
                    app.server = result.result();
                    promise.complete(app);
                } else {
                    app.failStart(result.cause(), promise);
                }
            });
        } catch (RuntimeException | Error failure) {
            app.failStart(failure, promise);
        }
        return promise.future();
    }

    /**
     * @param args 启动参数；端口来自 sample.port
     * @throws Exception 原生监听失败
     */
    public static void main(String[] args) throws Exception {
        var app = start(Integer.getInteger("sample.port", 8087)).toCompletionStage().toCompletableFuture().get();
        log.info("Vert.x Shiro listening {} (demo-memory)", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close().toCompletionStage().toCompletableFuture().join();
            log.info("VERTX_Shiro_CLOSED");
        }, "vertx-shiro-close"));
    }

    /**
     * 启动失败时清理部分装配的资源，再报告原始失败。
     *
     * @param failure 导致应用无法完成启动的原始异常
     * @param promise 报告当前应用启动结果的异步句柄
     */
    private void failStart(Throwable failure, io.vertx.core.Promise<VertxShiroApplication> promise) {
        close().onComplete(cleanup -> {
            if (cleanup.failed() && cleanup.cause() != failure) {
                failure.addSuppressed(cleanup.cause());
            }
            promise.fail(failure);
        });
    }

    /**
     * @return 实际监听端口
     */
    public int port() {
        return server.actualPort();
    }

    /**
     * 注册示例健康、原生鉴权及商品交易路由。
     *
     * @return 装配完毕的 Vert.x 路由器
     */
    private Router router() {
        Router router = Router.router(vertx);
        router.get("/health").handler(ctx -> ctx.response().end("UP"));
        router.route().handler(BodyHandler.create());
        new Ddd4jVertxWeb(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), request -> AuthenticationMode.DISABLED),
                new DefaultWebExceptionTranslator(), null, Json::encode).install(router);
        router.post("/auth/login").handler(ctx -> execute(ctx, "login"));
        router.post("/auth/logout").handler(ctx -> execute(ctx, "logout"));
        router.post("/orders/catalog/goods/:code").handler(ctx -> execute(ctx, "catalog-createGoods"));
        router.get("/orders/catalog/goods/:code").handler(ctx -> execute(ctx, "catalog-goods"));
        router.post("/orders/catalog/orders/:code").handler(ctx -> execute(ctx, "catalog-placeOrder"));
        router.post("/orders/catalog/orders/:code/payment").handler(ctx -> execute(ctx, "catalog-confirmPayment"));
        router.post("/orders/catalog/orders/:code/cancel").handler(ctx -> execute(ctx, "catalog-cancelOrder"));
        router.post("/orders/catalog/orders/:code/fulfill").handler(ctx -> execute(ctx, "catalog-fulfillOrder"));
        router.get("/orders/catalog/orders/:code").handler(ctx -> execute(ctx, "catalog-order"));
        return router;
    }

    /**
     * 拒绝关闭后到达的请求，并追踪已接收的操作直至执行完成。
     *
     * @param ctx       当前 Web 框架的请求上下文
     * @param operation 当前请求的商品交易操作或待执行的工作回调
     */
    private void execute(RoutingContext ctx, String operation) {
        if (closing) {
            ctx.fail(new WebStatusException(503, "application closing"));
            return;
        }
        String correlation = ctx.request().getHeader("X-Request-Id");
        var request = Ddd4jVertxContext.request(ctx).orElseThrow();
        Future<Object> task = vertx.executeBlocking(() -> {
            var before = ThreadContext.getResources();
            var nativeBefore = org.apache.shiro.util.ThreadContext.getSubject();
            org.apache.shiro.mgt.SecurityManager managerBefore = org.apache.shiro.util.ThreadContext.getSecurityManager();
            try {
                try (WebContextScope scope = WebContextScope.open(request); NativeScope nativeScope = new NativeScope(ctx.request().getHeader("Authorization"), auth)) {
                    Subject subject = new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider().getSubject();
                    ThreadContext.bind(subject);
                    if ("login".equals(operation)) {
                        var body = body(ctx);
                        return new JsonObject().put("token", auth.login(body.getString("username"), body.getString("password")));
                    }
                    // 原生引擎认证与 ddd4j Subject 读取同一会话，不使用测试主体。
                    if (!subject.isAuthenticated()) {
                        throw new WebStatusException(401, "authentication required");
                    }
                    if ("logout".equals(operation)) {
                        subject.logout();
                        return new JsonObject().put("success", true);
                    }
                    String permission = switch (operation) {
                        case "catalog-goods", "catalog-order" -> "order:read";
                        case "catalog-cancelOrder" -> "order:cancel";
                        default -> "order:write";
                    };
                    if (!subject.isPermitted(permission)) {
                        throw new WebStatusException(403, "permission denied");
                    }
                    if (subject.isPermitted(permission) != org.apache.shiro.SecurityUtils.getSubject().isPermitted(permission)) {
                        throw new IllegalStateException("native permission mismatch");
                    }
                    String tenant = ctx.request().getHeader("X-Tenant-Id");
                    String code = ctx.pathParam("code");
                    return switch (operation) {
                        case "catalog-createGoods" -> catalog.createGoods(tenant, code, ctx.body().asString());
                        case "catalog-goods" -> catalog.goods(tenant, code);
                        case "catalog-confirmPayment" -> catalog.confirmPayment(tenant, code, ctx.body().asString());
                        case "catalog-cancelOrder" -> catalog.cancelOrder(tenant, code);
                        case "catalog-fulfillOrder" -> catalog.fulfillOrder(tenant, code);
                        case "catalog-placeOrder" -> catalog.placeOrder(tenant, code, ctx.body().asString());
                        case "catalog-order" -> catalog.order(tenant, code);
                        default -> throw new IllegalArgumentException(operation);
                    };
                }
            } finally {
                completed.accept(correlation, before.equals(ThreadContext.getResources()) && org.apache.shiro.util.ThreadContext.getSubject() == nativeBefore
                        && org.apache.shiro.util.ThreadContext.getSecurityManager() == managerBefore);
            }
        });
        inFlight.add(task);
        task.onComplete(result -> {
            inFlight.remove(task);
            if (ctx.response().closed()) {
                return;
            }
            if (result.failed()) {
                ctx.fail(result.cause());
            } else if (result.result() instanceof Reply reply) {
                ctx.response().setStatusCode(reply.getStatus()).putHeader("content-type", "application/json").end(reply.getBody());
            } else {
                ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json").end(Json.encode(result.result()));
            }
        });
    }

    /**
     * 读取并要求存在可供认证请求处理的 JSON 对象。
     *
     * @param ctx 当前 Web 框架的请求上下文
     * @return 请求体中的 JSON 对象
     */
    private JsonObject body(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (Objects.isNull(body)) {
            throw new WebStatusException(400, "JSON body required");
        }
        return body;
    }

    /**
     * @return 停止接入，等待已进入业务的工作，再释放自身资源；不回滚已提交请求
     */
    public synchronized Future<Void> close() {
        if (Objects.isNull(closed)) {
            closing = true;
            closed = CompletableFuture.runAsync(() -> {
                try (Ddd4jVertxRuntime runtimeScope = runtime;
                     io.ddd4j.core.util.SubjectKitRegistrationScope subjectScope = subjects;
                     NativeAuth nativeScope = auth) {
                    if (Objects.nonNull(server)) {
                        server.close().toCompletionStage().toCompletableFuture().join();
                    }
                    for (Future<?> task : List.copyOf(inFlight)) {
                        try {
                            task.toCompletionStage().toCompletableFuture().join();
                        } catch (java.util.concurrent.CompletionException completedFailure) {
                            log.debug("Request completed with failure", completedFailure);
                        }
                    }
                } finally {
                    vertx.close().toCompletionStage().toCompletableFuture().join();
                }
            });
        }
        return Future.fromCompletionStage(closed);
    }
}
