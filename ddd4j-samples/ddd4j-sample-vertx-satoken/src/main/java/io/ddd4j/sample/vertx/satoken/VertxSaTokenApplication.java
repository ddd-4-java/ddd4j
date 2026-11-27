package io.ddd4j.sample.vertx.satoken;

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
 * 原生 SaToken 商品交易示例。
 */
@Slf4j
public final class VertxSaTokenApplication {
    /** \u4e3a\u5f53\u524d\u793a\u4f8b\u63d0\u4f9b HTTP \u670d\u52a1\u53ca\u5de5\u4f5c\u7ebf\u7a0b\u7684 Vert.x \u5b9e\u4f8b\u3002 */
    private final Vertx vertx = Vertx.vertx();
    /** \u5f53\u524d\u793a\u4f8b\u5df2\u88c5\u914d\u7684 Vert.x DDD4J \u8fd0\u884c\u65f6\u3002 */
    private final Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.create(vertx, List.of());
    /** \u89e3\u6790\u8bf7\u6c42\u8ba4\u8bc1\u4e3b\u4f53\u7684\u63d0\u4f9b\u5668\u6216\u7ba1\u7406\u5176\u6ce8\u518c\u751f\u547d\u5468\u671f\u7684\u4f5c\u7528\u57df\u3002 */
    private final io.ddd4j.core.util.SubjectKitRegistrationScope subjects = new io.ddd4j.core.util.SubjectKitRegistrationScope(
            new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider());
    /** \u5f53\u524d HTTP \u793a\u4f8b\u4f7f\u7528\u7684\u5171\u7528\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u3002 */
    private final CatalogEndpoints catalog;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u540e\u901a\u77e5\u6d4b\u8bd5\u6216\u5916\u90e8\u89c2\u5bdf\u8005\u7684\u56de\u8c03\u3002 */
    private final BiConsumer<String, Boolean> completed;
    /** \u5df2\u7ecf\u63d0\u4ea4\u4e14\u5c1a\u672a\u5b8c\u6210\u7684\u8bf7\u6c42\u5904\u7406\u4efb\u52a1\u3002 */
    private final Set<Future<?>> inFlight = ConcurrentHashMap.newKeySet();
    /** \u5904\u7406\u5f53\u524d\u793a\u4f8b\u5e94\u7528\u767b\u5f55\u72b6\u6001\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
    private NativeAuth auth;
    /** \u7531\u5f53\u524d\u5e94\u7528\u6301\u6709\u5e76\u5728\u505c\u6b62\u65f6\u5173\u95ed\u7684 Vert.x HTTP \u670d\u52a1\u5668\u3002 */
    private HttpServer server;
    /** \u6807\u8bb0\u5f53\u524d\u793a\u4f8b\u662f\u5426\u5df2\u7ecf\u5f00\u59cb\u5173\u95ed\u3002 */
    private volatile boolean closing;
    /** \u7528\u4e8e\u89c2\u5bdf\u5f02\u6b65\u505c\u6b62\u53ca\u8d44\u6e90\u56de\u6536\u7ed3\u679c\u7684\u5b8c\u6210\u4fe1\u53f7\u3002 */
    private CompletableFuture<Void> closed;

    /**
     * \u4fdd\u5b58\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u53ca\u6d41\u7a0b\u7ed3\u675f\u65f6\u4f7f\u7528\u7684\u89c2\u5bdf\u56de\u8c03\u3002
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     * @param completed \u8bf7\u6c42\u5904\u7406\u5b8c\u6210\u65f6\u8c03\u7528\u7684\u89c2\u5bdf\u56de\u8c03\u6216\u5b8c\u6210\u4fe1\u53f7
     */
    private VertxSaTokenApplication(CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        this.catalog = catalog;
        this.completed = completed;
    }

    /**
     * @param port 监听端口
     * @return 已启动原生应用
     */
    public static Future<VertxSaTokenApplication> start(int port) {
        return start(port, CatalogRuntime.start(), (id, clean) -> {
        });
    }

    // 所有入口共用路由；包内观测器只观察工作线程恢复后的结果，不修改上下文。
    /**
     * \u542f\u52a8\u5305\u542b\u5546\u54c1\u4ea4\u6613\u8def\u7531\u7684 Vert.x HTTP \u670d\u52a1\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     * @param completed \u8bf7\u6c42\u5904\u7406\u5b8c\u6210\u65f6\u8c03\u7528\u7684\u89c2\u5bdf\u56de\u8c03\u6216\u5b8c\u6210\u4fe1\u53f7
     * @return \u5e94\u7528\u542f\u52a8\u540e\u7684\u8fd0\u884c\u5b9e\u4f8b\u6216\u5176\u5f02\u6b65\u7ed3\u679c
     */
    static Future<VertxSaTokenApplication> start(int port, CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        var app = new VertxSaTokenApplication(catalog, completed);
        var promise = io.vertx.core.Promise.<VertxSaTokenApplication>promise();
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
        var app = start(Integer.getInteger("sample.port", 8086)).toCompletionStage().toCompletableFuture().get();
        log.info("Vert.x SaToken listening {} (demo-memory)", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close().toCompletionStage().toCompletableFuture().join();
            log.info("VERTX_SaToken_CLOSED");
        }, "vertx-satoken-close"));
    }

    /**
     * \u542f\u52a8\u5931\u8d25\u65f6\u6e05\u7406\u90e8\u5206\u88c5\u914d\u7684\u8d44\u6e90\uff0c\u518d\u62a5\u544a\u539f\u59cb\u5931\u8d25\u3002
     * @param failure \u5bfc\u81f4\u5e94\u7528\u65e0\u6cd5\u5b8c\u6210\u542f\u52a8\u7684\u539f\u59cb\u5f02\u5e38
     * @param promise \u62a5\u544a\u5f53\u524d\u5e94\u7528\u542f\u52a8\u7ed3\u679c\u7684\u5f02\u6b65\u53e5\u67c4
     */
    private void failStart(Throwable failure, io.vertx.core.Promise<VertxSaTokenApplication> promise) {
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
     * \u6ce8\u518c\u793a\u4f8b\u5065\u5eb7\u3001\u539f\u751f\u9274\u6743\u53ca\u5546\u54c1\u4ea4\u6613\u8def\u7531\u3002
     * @return \u88c5\u914d\u5b8c\u6bd5\u7684 Vert.x \u8def\u7531\u5668
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
     * \u62d2\u7edd\u5173\u95ed\u540e\u5230\u8fbe\u7684\u8bf7\u6c42\uff0c\u5e76\u8ffd\u8e2a\u5df2\u63a5\u6536\u7684\u64cd\u4f5c\u76f4\u81f3\u6267\u884c\u5b8c\u6210\u3002
     * @param ctx \u5f53\u524d Web \u6846\u67b6\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param operation \u5f53\u524d\u8bf7\u6c42\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u6216\u5f85\u6267\u884c\u7684\u5de5\u4f5c\u56de\u8c03
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
            var nativeContext = cn.dev33.satoken.SaManager.getSaTokenContext();
            var nativeBefore = nativeContext.isValid() ? nativeContext.getModelBox() : null;
            try {
                try (WebContextScope scope = WebContextScope.open(request); NativeScope nativeScope = new NativeScope(ctx)) {
                    Subject subject = new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider().getSubject();
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
                    if (subject.isPermitted(permission) != cn.dev33.satoken.stp.StpUtil.hasPermission(permission)) {
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
                completed.accept(correlation, before.equals(ThreadContext.getResources()) && (nativeContext.isValid() ? nativeContext.getModelBox() : null) == nativeBefore);
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
     * \u8bfb\u53d6\u5e76\u8981\u6c42\u5b58\u5728\u53ef\u4f9b\u8ba4\u8bc1\u8bf7\u6c42\u5904\u7406\u7684 JSON \u5bf9\u8c61\u3002
     * @param ctx \u5f53\u524d Web \u6846\u67b6\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u8bf7\u6c42\u4f53\u4e2d\u7684 JSON \u5bf9\u8c61
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
