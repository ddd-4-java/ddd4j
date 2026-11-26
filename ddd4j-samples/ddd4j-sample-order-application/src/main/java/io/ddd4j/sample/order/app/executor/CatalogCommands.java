package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.command.ConfirmTradePaymentCmd;
import io.ddd4j.sample.order.client.command.ChangeTradeOrderCmd;
import io.ddd4j.sample.order.client.command.CreateGoodsSpuCmd;
import io.ddd4j.sample.order.client.command.PlaceTradeOrderCmd;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** 商品建档和下单的应用执行器，供各框架命令总线注册。 */
@RequiredArgsConstructor
public final class CatalogCommands implements CommandExecutor<Command> {
    private final CatalogService service;

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(CreateGoodsSpuCmd.class, PlaceTradeOrderCmd.class, ConfirmTradePaymentCmd.class, ChangeTradeOrderCmd.class);
    }

    @Override
    public Result<Object> execute(Command command) {
        if (command instanceof CreateGoodsSpuCmd create) {
            return Result.ok(service.create(create));
        }
        if (command instanceof PlaceTradeOrderCmd place) {
            return Result.ok(service.place(place));
        }
        if (command instanceof ConfirmTradePaymentCmd payment) {
            return Result.ok(service.confirmPayment(payment));
        }
        if (command instanceof ChangeTradeOrderCmd change) {
            return Result.ok(service.change(change));
        }
        throw new IllegalArgumentException("不支持的商品交易命令");
    }
}
