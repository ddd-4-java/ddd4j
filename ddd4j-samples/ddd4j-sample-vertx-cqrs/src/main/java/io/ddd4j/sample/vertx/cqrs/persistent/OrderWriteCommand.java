package io.ddd4j.sample.vertx.cqrs.persistent;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;

/** 将既有共享命令包装为运行时命令，不复制业务规则。
 */
public final class OrderWriteCommand implements Command {
    final CreateOrderCommand create;
    final AddOrderLineCommand line;
    final String orderId;
    final String paymentKey;
    /**
     * @param create 创建输入
     * @param line 明细输入
     * @param orderId 支付订单
     * @param paymentKey 幂等键
     */
    public OrderWriteCommand(CreateOrderCommand create, AddOrderLineCommand line, String orderId, String paymentKey) {
        this.create = create; this.line = line; this.orderId = orderId; this.paymentKey = paymentKey;
    }
}
