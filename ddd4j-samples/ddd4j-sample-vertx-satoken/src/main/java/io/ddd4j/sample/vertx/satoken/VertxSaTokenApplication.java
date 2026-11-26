package io.ddd4j.sample.vertx.satoken;

import com.google.common.collect.ImmutableList;
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
    private final Vertx vertx = Vertx.vertx();
    private final Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.create(vertx, ImmutableList.of());
    private final io.ddd4j.core.util.SubjectKitRegistrationScope subjects = new io.ddd4j.core.util.SubjectKitRegistrationScope(
            new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider());
    private final CatalogEndpoints catalog;
    private final BiConsumer<String, Boolean> completed;
    private final Set<Future<?>> inFlight = ConcurrentHashMap.newKeySet();
    private NativeAuth auth;
    private HttpServer server;
    private volatile boolean closing;
    private CompletableFuture<Void> closed;

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
    static Future<VertxSaTokenApplication> start(int port, CatalogEndpoints catalog, BiConsumer<String, Boolean> completed) {
        VertxSaTokenApplication app = new VertxSaTokenApplication(catalog, completed);
        io.vertx.core.Promise<VertxSaTokenApplication> promise = io.vertx.core.Promise.<VertxSaTokenApplication>promise();
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
        VertxSaTokenApplication app = start(Integer.getInteger("sample.port", 8086)).toCompletionStage().toCompletableFuture().get();
        log.info("Vert.x SaToken listening {} (demo-memory)", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close().toCompletionStage().toCompletableFuture().join();
            log.info("VERTX_SaToken_CLOSED");
        }, "vertx-satoken-close"));
    }

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

    private void execute(RoutingContext ctx, String operation) {
        if (closing) {
            ctx.fail(new WebStatusException(503, "application closing"));
            return;
        }
        String correlation = ctx.request().getHeader("X-Request-Id");
        io.ddd4j.web.core.context.WebRequestContext request = Ddd4jVertxContext.request(ctx).orElseThrow(java.util.NoSuchElementException::new);
        Future<Object> task = vertx.executeBlocking(() -> {
            java.util.Map<Object, Object> before = ThreadContext.getResources();
            cn.dev33.satoken.context.SaTokenContext nativeContext = cn.dev33.satoken.SaManager.getSaTokenContext();
            cn.dev33.satoken.context.model.SaTokenContextModelBox nativeBefore = nativeContext.isValid() ? nativeContext.getModelBox() : null;
            try {
                try (WebContextScope scope = WebContextScope.open(request); NativeScope nativeScope = new NativeScope(ctx)) {
                    Subject subject = new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider().getSubject();
                    ThreadContext.bind(subject);
                    if ("login".equals(operation)) {
                        io.vertx.core.json.JsonObject body = body(ctx);
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
                    String permission;
                    switch (operation) {
                        case "catalog-goods":
                        case "catalog-order":
                            permission = "order:read";
                            break;
                        case "catalog-cancelOrder":
                            permission = "order:cancel";
                            break;
                        default:
                            permission = "order:write";
                            break;
                    }
                    if (!subject.isPermitted(permission)) {
                        throw new WebStatusException(403, "permission denied");
                    }
                    if (subject.isPermitted(permission) != cn.dev33.satoken.stp.StpUtil.hasPermission(permission)) {
                        throw new IllegalStateException("native permission mismatch");
                    }
                    String tenant = ctx.request().getHeader("X-Tenant-Id");
                    String code = ctx.pathParam("code");
                    switch (operation) {
                        case "catalog-createGoods":
                            return catalog.createGoods(tenant, code, ctx.body().asString());
                        case "catalog-goods":
                            return catalog.goods(tenant, code);
                        case "catalog-confirmPayment":
                            return catalog.confirmPayment(tenant, code, ctx.body().asString());
                        case "catalog-cancelOrder":
                            return catalog.cancelOrder(tenant, code);
                        case "catalog-fulfillOrder":
                            return catalog.fulfillOrder(tenant, code);
                        case "catalog-placeOrder":
                            return catalog.placeOrder(tenant, code, ctx.body().asString());
                        case "catalog-order":
                            return catalog.order(tenant, code);
                        default:
                            throw new IllegalArgumentException(operation);
                    }
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
            } else if (result.result() instanceof Reply) {
                Reply reply = (Reply) result.result();
                ctx.response().setStatusCode(reply.getStatus()).putHeader("content-type", "application/json").end(reply.getBody());
            } else {
                ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json").end(Json.encode(result.result()));
            }
        });
    }

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
                    for (Future<?> task : ImmutableList.copyOf(inFlight)) {
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
