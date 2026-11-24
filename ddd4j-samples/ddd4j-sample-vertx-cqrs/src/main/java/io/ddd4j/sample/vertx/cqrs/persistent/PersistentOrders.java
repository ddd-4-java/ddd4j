package io.ddd4j.sample.vertx.cqrs.persistent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.ddd4j.vertx.command.VertxCommandBus;
import io.ddd4j.vertx.cqrs.VertxProjectionScheduler;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/** 持久化 Order 原生入口；专用订单数据库/事件源，沿用现有表，不执行迁移。 */
@Slf4j
public final class PersistentOrders {
    private final Vertx vertx;
    private final EntityManagerFactory factory;
    private final Ddd4jVertxRuntime runtime;
    private final VertxProjectionScheduler scheduler;
    private final Set<CompletableFuture<Void>> accepted = new HashSet<>();
    private final CommandBus commands;
    private final OrderProjection projection;
    private final JdbcOrderReadModelPort queries;
    private HttpServer server;
    private volatile boolean closing;
    private CompletableFuture<Void> closed;

    private PersistentOrders(DataSource source, String schema) {
        var configuration = new Configuration().addAnnotatedClass(StoredEventEntity.class)
                .addAnnotatedClass(ProjectionPositionEntity.class);
        configuration.getProperties().put("hibernate.connection.datasource", source);
        configuration.setProperty("hibernate.hbm2ddl.auto", schema);
        factory = configuration.buildSessionFactory();
        vertx = Vertx.vertx();
        scheduler = new VertxProjectionScheduler(vertx);
        var tx = new JdbcOrderTransactionPort(source);
        var repo = new JdbcOrderRepository(tx);
        queries = new JdbcOrderReadModelPort(tx);
        var mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var outbox = new JdbcOutboxPort(tx, mapper);
        var orders = new OrderApplicationService(repo, outbox, new DeferredOrderReadModel(queries), new InMemoryOrderAdapters(), tx);
        projection = new OrderProjection(repo, outbox, queries, tx, factory, mapper);
        List<io.ddd4j.core.cqrs.command.CommandExecutor<?>> executors = List.of(new OrderCommandExecutor(orders));
        commands = VertxCommandBus.create(vertx, executors);
        Ddd4jVertxRuntime ownedRuntime = null;
        try {
            // 包裹的是公共 Runner 的完整 Runnable：位置读取、事件读取、视图提交和检查点都在同一准入内。
            ViewScheduler trackedScheduler = (name, cron, task) -> scheduler.schedule(name, cron,
                    () -> runProjection(task));
            ownedRuntime = Ddd4jVertxRuntime.createWithProjection(vertx, executors,
                    new PersistentPositions(factory), projection.reader(), List.of(projection.view()), trackedScheduler);
            ownedRuntime.start();
            runtime = ownedRuntime;
        } catch (RuntimeException | Error failure) {
            if (Objects.nonNull(ownedRuntime)) { ownedRuntime.close(); }
            scheduler.close();
            vertx.close().toCompletionStage().toCompletableFuture().join();
            factory.close();
            throw failure;
        }
    }
    /**
 * @param port HTTP 端口
 * @param source 专用订单数据库
 * @param schema validate 或验收 fixture 的 create
 * @return 启动实例
 */
    public static Future<PersistentOrders> start(int port, DataSource source, String schema) {
        var app = new PersistentOrders(source, schema);
        Promise<PersistentOrders> result = Promise.promise();
        try {
            app.vertx.createHttpServer().requestHandler(app.router()).listen(port, "127.0.0.1").onComplete(started -> {
                if (started.succeeded()) {
                    app.server = started.result();
                    result.complete(app);
                } else {
                    app.failStart(result, started.cause());
                }
            });
        } catch (RuntimeException | Error failure) {
            app.failStart(result, failure);
        }
        return result.future();
    }

    private void failStart(Promise<PersistentOrders> result, Throwable failure) {
        close().onComplete(cleaned -> {
            if (cleaned.failed() && cleaned.cause() != failure) {
                failure.addSuppressed(cleaned.cause());
            }
            result.fail(failure);
        });
    }

    /**
 * @return 实际端口
 */
    public int port() { return server.actualPort(); }
    /**
 * @return 既有源事件/位置/持久快照访问，非 HTTP 兜底
 */
    public OrderProjection projection() { return projection; }
    /**
 * @return 应用拥有的实体工厂
 */
    public EntityManagerFactory entities() { return factory; }
    /**
 * @return 公共 Runner 的 worker 任务
 */
    public Future<Void> project() { return admit(runtime::triggerProjections); }
    private Router router() {
        Router router = Router.router(vertx); router.route().handler(BodyHandler.create());
        router.get("/health").handler(ctx -> ctx.response().end("UP"));
        router.post("/orders").handler(ctx -> execute(ctx, () -> {
            var body = body(ctx);
            Result<String> result = commands.execute(new OrderWriteCommand(new CreateOrderCommand(body.getString("orderNo"),
                    body.getString("buyerId"), body.getString("buyerName")), null, null, null));
            if (!result.isSuccess()) { throw new IllegalStateException(result.getMessage()); }
            return new JsonObject().put("success", true).put("orderId", result.getData());
        }, 201));
        router.get("/orders/:id").handler(ctx -> execute(ctx, () -> queries.findProjectionById(ctx.pathParam("id"))
                .map(order -> new JsonObject().put("orderId", order.id()).put("orderNo", order.orderNo())
                        .put("buyerId", order.buyerId()).put("buyerName", order.buyerName()).put("status", order.status().name()))
                .orElse(null), 200));
        router.post("/orders/:id/lines").handler(ctx -> execute(ctx, () -> {
            var body = body(ctx);
            return write(new OrderWriteCommand(null, new AddOrderLineCommand(ctx.pathParam("id"), body.getString("goodsId"),
                    body.getString("goodsName"), body.getInteger("quantity"), new BigDecimal(body.getValue("unitPrice").toString())), null, null));
        }, 200));
        router.post("/orders/:id/pay").handler(ctx -> execute(ctx, () -> {
            String key = ctx.request().getHeader("Idempotency-Key");
            if (cn.hutool.core.util.StrUtil.isBlank(key)) { throw new IllegalArgumentException("Idempotency-Key required"); }
            return write(new OrderWriteCommand(null, null, ctx.pathParam("id"), key));
        }, 200));
        router.post("/projections/orders/run").handler(ctx -> project().onComplete(result -> {
            if (result.failed()) { failure(ctx, result.cause()); }
            else { ctx.response().putHeader("Content-Type", "application/json").end("{\"success\":true}"); }
        }));
        return router;
    }
    private JsonObject write(OrderWriteCommand command) {
        Result<String> result = commands.execute(command);
        if (!result.isSuccess()) { throw new IllegalStateException(result.getMessage()); }
        return new JsonObject().put("success", true).put("orderId", result.getData());
    }
    private JsonObject body(RoutingContext ctx) {
        return Objects.requireNonNull(ctx.body().asJsonObject(), "JSON body required");
    }
    private void execute(RoutingContext ctx, Callable<JsonObject> work, int status) {
        admit(() -> runtime.executeBlocking(work)).onComplete(result -> {
            if (ctx.response().closed()) { return; }
            if (result.failed()) { failure(ctx, result.cause()); }
            else if (Objects.isNull(result.result())) { ctx.response().setStatusCode(404).end(); }
            else { ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json").end(result.result().encode()); }
        });
    }
    private void failure(RoutingContext ctx, Throwable error) {
        if (ctx.response().closed()) { return; }
        int status = error instanceof IllegalArgumentException ? 400 : 409;
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("success", false).put("message", error.getMessage()).encode());
    }
    // 本应用的准入锁同时覆盖请求提交和 timer 进入，关闭后的排队 timer 不再进入存储。
    private synchronized CompletableFuture<Void> enter() {
        if (closing) { return null; }
        var finished = new CompletableFuture<Void>();
        accepted.add(finished);
        return finished;
    }

    private synchronized void finish(CompletableFuture<Void> finished) {
        accepted.remove(finished);
        finished.complete(null);
    }

    private synchronized <T> Future<T> admit(Supplier<Future<T>> submission) {
        CompletableFuture<Void> finished = enter();
        if (Objects.isNull(finished)) { return Future.failedFuture(new IllegalStateException("application closing")); }
        try {
            Future<T> task = submission.get();
            task.onComplete(ignored -> finish(finished));
            return task;
        } catch (RuntimeException | Error failure) {
            finish(finished);
            throw failure;
        }
    }

    private void runProjection(Runnable task) {
        CompletableFuture<Void> finished = enter();
        if (Objects.isNull(finished)) { return; }
        try { task.run(); }
        finally { finish(finished); }
    }

    /**
     * 关闭准入及后续 timer，等待已接纳请求和已进入的完整 Runner，再释放存储和 Vert.x。
     * @return 排空及资源关闭结果；外部数据源仍归调用方
     */
    public synchronized Future<Void> close() {
        if (Objects.isNull(closed)) {
            closing = true;
            var drained = CompletableFuture.allOf(accepted.toArray(CompletableFuture[]::new));
            log.info("VERTX_CQRS_DRAINING accepted={}", accepted.size());
            closed = CompletableFuture.runAsync(() -> {
                // stop 取消后续 timer，但保留 manager 对已接纳手动触发的同步执行能力。
                runtime.projectionManager().orElseThrow().stop();
                scheduler.close();
                drained.join();
                log.info("VERTX_CQRS_DRAINED");
                try {
                    if (Objects.nonNull(server)) { server.close().toCompletionStage().toCompletableFuture().join(); }
                    runtime.close();
                    vertx.close().toCompletionStage().toCompletableFuture().join();
                } finally { factory.close(); }
            });
        }
        return Future.fromCompletionStage(closed);
    }
}
