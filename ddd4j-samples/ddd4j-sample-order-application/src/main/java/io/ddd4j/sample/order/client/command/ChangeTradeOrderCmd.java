package io.ddd4j.sample.order.client.command;

import io.ddd4j.core.cqrs.command.Command;
import lombok.Value;

/**
 * 交易订单已有状态的变更命令。
 */
@Value
public class ChangeTradeOrderCmd implements Command {
    /** \u4e1a\u52a1\u6240\u5c5e\u79df\u6237\u6807\u8bc6\u3002 */
    String tenantId;
    /** \u79df\u6237\u5185\u552f\u4e00\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002 */
    String orderNo;
    /** \u53d6\u6d88\u6216\u5b8c\u6210\u5c65\u7ea6\u7684\u72b6\u6001\u53d8\u66f4\u52a8\u4f5c\u3002 */
    Action action;

    /** 允许执行的订单状态迁移。 */
    public enum Action {
        /** 取消尚未支付的订单。 */
        CANCEL,
        /** 完成已确认订单的履约。 */
        FULFILL
    }
}
