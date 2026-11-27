package io.ddd4j.sample.order.app.executor;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.api.R;
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
    /** \u843d\u5b9e\u5546\u54c1\u4ea4\u6613\u5199\u7528\u4f8b\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final CatalogService service;

    /**
     * 声明当前处理器可以接收的命令类型。
     *
     * @return 当前处理器声明可接收的命令类型集合
     */
    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(CreateGoodsSpuCmd.class, PlaceTradeOrderCmd.class,
                ConfirmTradePaymentCmd.class, ChangeTradeOrderCmd.class)));
    }

    /**
     * 用当前处理器执行传入命令或操作。
     *
     * <p>此实现调用 <code>R.ok(service.create((CreateGoodsSpuCmd)command))</code>。</p>
     *
     * @param command 命令对象
     * @return 根据执行路径返回<code>R.ok(service.create((CreateGoodsSpuCmd)command))</code>、<code>R.ok(service.place((PlaceTradeOrderCmd)command))</code>、<code>R.ok(service.confirmPayment((ConfirmTradePaymentCmd)command))</code>
     */
    @Override
    public R<Object> execute(Command command) {
        if (command instanceof CreateGoodsSpuCmd) {
            return R.ok(service.create((CreateGoodsSpuCmd) command));
        }
        if (command instanceof PlaceTradeOrderCmd) {
            return R.ok(service.place((PlaceTradeOrderCmd) command));
        }
        if (command instanceof ConfirmTradePaymentCmd) {
            return R.ok(service.confirmPayment((ConfirmTradePaymentCmd) command));
        }
        if (command instanceof ChangeTradeOrderCmd) {
            return R.ok(service.change((ChangeTradeOrderCmd) command));
        }
        throw new IllegalArgumentException("不支持的商品交易命令");
    }
}
