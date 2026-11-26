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

    public enum Action {
        CANCEL,
        FULFILL
    }
}
