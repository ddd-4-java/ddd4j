package io.ddd4j.sample.vertx.shiro;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

/** 原生 Shiro 的小型 Order 示例；会话与订单仅用于单实例内存演示。 */
@Slf4j
public final class VertxShiroApplication {
    private final Vertx vertx = Vertx.vertx();
    private final Ddd4jVertxRuntime runtime = Ddd4jVertxRuntime.create(vertx, List.of());
    private NativeAuth auth;
    private final io.ddd4j.core.util.SubjectKitRegistrationScope subjects = new io.ddd4j.core.util.SubjectKitRegistrationScope(
            new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider());
    private final OrderApplicationService orders;
    private final BiConsumer<String, Boolean> completed;
    private final Set<Future<?>> inFlight = ConcurrentHashMap.newKeySet();
    private HttpServer server;
    private volatile boolean closing;
    private CompletableFuture<Void> closed;
    private VertxShiroApplication(OrderApplicationService orders, BiConsumer<String, Boolean> completed) {
        this.orders = orders; this.completed = completed;
    }
    /**
 * @param port 监听端口
 * @return 已启动原生应用
 */
    public static Future<VertxShiroApplication> start(int port) {
        var memory = new InMemoryOrderAdapters();
        return start(port, new OrderApplicationService(memory, memory, memory, memory, memory), (id, clean) -> { });
    }
    // 所有入口共用路由；包内观测器只观察工作线程恢复后的结果，不修改上下文。
    static Future<VertxShiroApplication> start(int port, OrderApplicationService orders, BiConsumer<String, Boolean> completed) {
        var app = new VertxShiroApplication(orders, completed);
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
    public int port() { return server.actualPort(); }
    private Router router() {
        Router router = Router.router(vertx);
        router.get("/health").handler(ctx -> ctx.response().end("UP"));
        router.route().handler(BodyHandler.create());
        new Ddd4jVertxWeb(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), request -> AuthenticationMode.DISABLED),
                new DefaultWebExceptionTranslator(), null, Json::encode).install(router);
        router.post("/auth/login").handler(ctx -> execute(ctx, "login"));
        router.post("/auth/logout").handler(ctx -> execute(ctx, "logout"));
        router.post("/orders").handler(ctx -> execute(ctx, "create"));
        router.get("/orders/:id").handler(ctx -> execute(ctx, "read"));
        router.post("/orders/:id/lines").handler(ctx -> execute(ctx, "line"));
        return router;
    }
    private void execute(RoutingContext ctx, String operation) {
        if (closing) { ctx.fail(new WebStatusException(503, "application closing")); return; }
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
                    if (!subject.isAuthenticated()) { throw new WebStatusException(401, "authentication required"); }
                    if ("logout".equals(operation)) { subject.logout(); return new JsonObject().put("success", true); }
                    String permission = "read".equals(operation) ? "order:read" : "order:write";
                    if (!subject.isPermitted(permission)) { throw new WebStatusException(403, "permission denied"); }
                    if (subject.isPermitted(permission) != org.apache.shiro.SecurityUtils.getSubject().isPermitted(permission)) { throw new IllegalStateException("native permission mismatch"); }
                    if ("read".equals(operation)) { return orders.find(ctx.pathParam("id")); }
                    var body = body(ctx);
                    if ("create".equals(operation)) {
                        String id = orders.create(new CreateOrderCommand(body.getString("orderNo"), body.getString("buyerId"), body.getString("buyerName"))).id();
                        return orders.find(id);
                    }
                    orders.addLine(new AddOrderLineCommand(ctx.pathParam("id"), body.getString("goodsId"),
                            body.getString("goodsName"), body.getInteger("quantity"), new BigDecimal(body.getValue("unitPrice").toString())));
                    return orders.find(ctx.pathParam("id"));
                }
            } finally {
                completed.accept(correlation, before.equals(ThreadContext.getResources()) && org.apache.shiro.util.ThreadContext.getSubject() == nativeBefore
                        && org.apache.shiro.util.ThreadContext.getSecurityManager() == managerBefore);
            }
        });
        inFlight.add(task);
        task.onComplete(result -> {
            inFlight.remove(task);
            if (ctx.response().closed()) { return; }
            if (result.failed()) { ctx.fail(result.cause()); }
            else { ctx.response().setStatusCode("create".equals(operation) ? 201 : 200)
                    .putHeader("Content-Type", "application/json").end(Json.encode(result.result())); }
        });
    }
    private JsonObject body(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (Objects.isNull(body)) { throw new WebStatusException(400, "JSON body required"); }
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
                    if (Objects.nonNull(server)) { server.close().toCompletionStage().toCompletableFuture().join(); }
                    for (Future<?> task : List.copyOf(inFlight)) {
                        try { task.toCompletionStage().toCompletableFuture().join(); }
                        catch (java.util.concurrent.CompletionException completedFailure) { log.debug("Request completed with failure", completedFailure); }
                    }
                } finally {
                    vertx.close().toCompletionStage().toCompletableFuture().join();
                }
            });
        }
        return Future.fromCompletionStage(closed);
    }
    /**
 * @param args 启动参数；端口来自 sample.port
 * @throws Exception 原生监听失败
 */
    public static void main(String[] args) throws Exception {
        var app = start(Integer.getInteger("sample.port", 8087)).toCompletionStage().toCompletableFuture().get();
        log.info("Vert.x Shiro listening {} (demo-memory)", app.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close().toCompletionStage().toCompletableFuture().join(); log.info("VERTX_Shiro_CLOSED");
        }, "vertx-shiro-close"));
    }
}
