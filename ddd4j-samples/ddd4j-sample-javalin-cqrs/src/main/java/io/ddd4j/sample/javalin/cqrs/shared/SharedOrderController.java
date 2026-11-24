package io.ddd4j.sample.javalin.cqrs.shared;

import java.util.NoSuchElementException;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.javalin.apibuilder.ApiBuilder;

/** 独立持久 CQRS 主入口；保留旧内存 Order/Goods 的 /api 路由与 DTO。 */
public final class SharedOrderController {
    private final SharedCqrsRuntime runtime;
    /** @param runtime 已启动的持久化运行实例 */
    public SharedOrderController(SharedCqrsRuntime runtime) { this.runtime = runtime; }
    /** 注册真实共享订单命令/独立查询与显式投影操作。 */
    public void routes() {
        ApiBuilder.post("/sample/orders", ctx -> ctx.json(runtime.commands().<String>execute(new OrderWriteCommand(
                ctx.bodyAsClass(CreateOrderCommand.class), null, null, null)).data().orElseThrow(() -> new NoSuchElementException("No value present"))));
        ApiBuilder.post("/sample/orders/lines", ctx -> ctx.json(runtime.commands().<String>execute(new OrderWriteCommand(
                null, ctx.bodyAsClass(AddOrderLineCommand.class), null, null)).data().orElseThrow(() -> new NoSuchElementException("No value present"))));
        ApiBuilder.post("/sample/orders/{id}/payment", ctx -> ctx.json(runtime.commands().<String>execute(new OrderWriteCommand(
                null, null, ctx.pathParam("id"), ctx.queryParam("key"))).data().orElseThrow(() -> new NoSuchElementException("No value present"))));
        ApiBuilder.get("/sample/orders/{id}", ctx -> ctx.json(runtime.queries().findProjectionById(ctx.pathParam("id")).orElseThrow(() -> new NoSuchElementException("No value present"))));
        ApiBuilder.post("/sample/orders/query", ctx -> ctx.json(runtime.queries().query(ctx.bodyAsClass(OrderQuery.class))));
        ApiBuilder.post("/sample/projections/orders/run", ctx -> { runtime.projection().project(); ctx.json("projected"); });
    }
}
