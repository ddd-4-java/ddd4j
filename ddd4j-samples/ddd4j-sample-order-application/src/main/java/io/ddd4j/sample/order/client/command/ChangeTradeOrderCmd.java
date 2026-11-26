package io.ddd4j.sample.order.client.command;

import io.ddd4j.core.cqrs.command.Command;
import lombok.Value;

/**
 * 交易订单已有状态的变更命令。
 */
@Value
public class ChangeTradeOrderCmd implements Command {
    String tenantId;
    String orderNo;
    Action action;

    /** 允许执行的订单状态迁移。 */
    public enum Action {
        /** 取消尚未支付的订单。 */
        CANCEL,
        /** 完成已确认订单的履约。 */
        FULFILL
    }
}
