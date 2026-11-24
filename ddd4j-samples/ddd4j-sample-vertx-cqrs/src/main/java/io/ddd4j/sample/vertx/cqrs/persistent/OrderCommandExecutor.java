package io.ddd4j.sample.vertx.cqrs.persistent;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.application.OrderApplicationService;
import java.util.Objects;
import java.util.Collections;
import java.util.Set;

/** 原生 Vert.xCommandBus 发现的执行器，实际写入全部委托共享 Order 服务。
 */


public class OrderCommandExecutor implements CommandExecutor<OrderWriteCommand> {
    private final OrderApplicationService orders;
    /**
     * @param orders 共享应用服务
     */
    
    public OrderCommandExecutor(OrderApplicationService orders) { this.orders = orders; }
    @Override public Set<Class<? extends Command>> supportedCommands() { return Collections.singleton(OrderWriteCommand.class); }
    @Override public Result<String> execute(OrderWriteCommand command) {
        if (Objects.nonNull(command.create)) { return Result.ok(orders.create(command.create).id()); }
        if (Objects.nonNull(command.line)) { return Result.ok(orders.addLine(command.line).id()); }
        return Result.ok(orders.pay(command.orderId, command.paymentKey).id());
    }
}
