package io.ddd4j.sample.order.client.command;

import io.ddd4j.core.cqrs.command.Command;
import lombok.Value;

/** 按租户和订单号确认已收款金额，金额单位为分。 */
@Value
public class ConfirmTradePaymentCmd implements Command {
    String tenantId;
    String orderNo;
    Long paidAmountCent;
}
