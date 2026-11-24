package io.ddd4j.sample.quarkus.cqrs.shared;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
/** CDI 命令执行器；订单和 Outbox 同一 JDBC 事务由共享应用服务拥有。 */
@ApplicationScoped
public class OrderCommandExecutor implements CommandExecutor<OrderWriteCommand> {
    @Inject SharedOrderStorage storage;
    @Override public Set<Class<? extends Command>> supportedCommands() { return Collections.unmodifiableSet(Collections.singleton(OrderWriteCommand.class)); }
    @Override public Result<String> execute(OrderWriteCommand command) {
        var orders = storage.service();
        if (Objects.nonNull(command.create)) { return Result.ok(orders.create(command.create).id()); }
        if (Objects.nonNull(command.line)) { return Result.ok(orders.addLine(command.line).id()); }
        return Result.ok(orders.pay(command.orderId, command.paymentKey).id());
    }
}
