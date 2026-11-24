package io.ddd4j.sample.helidon.cqrs.shared;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.application.OrderApplicationService;
import java.util.Objects;
import java.util.Set;

/** 原生 HelidonCommandBus 发现的执行器，实际写入全部委托共享 Order 服务。
 */
@jakarta.enterprise.context.ApplicationScoped

public class OrderCommandExecutor implements CommandExecutor<OrderWriteCommand> {
    private final jakarta.enterprise.inject.Instance<OrderApplicationService> orders;
    /**
     * @param orders 共享应用服务
     */
    @jakarta.inject.Inject
    public OrderCommandExecutor(jakarta.enterprise.inject.Instance<OrderApplicationService> orders) { this.orders = orders; }
    @Override public Set<Class<? extends Command>> supportedCommands() { return Set.of(OrderWriteCommand.class); }
    @Override public Result<String> execute(OrderWriteCommand command) {
        if (Objects.nonNull(command.create)) { return Result.ok(orders.get().create(command.create).id()); }
        if (Objects.nonNull(command.line)) { return Result.ok(orders.get().addLine(command.line).id()); }
        return Result.ok(orders.get().pay(command.orderId, command.paymentKey).id());
    }
}
