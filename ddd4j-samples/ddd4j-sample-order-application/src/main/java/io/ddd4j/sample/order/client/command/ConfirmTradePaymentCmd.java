package io.ddd4j.sample.order.client.command;

import io.ddd4j.core.cqrs.command.Command;
import lombok.Value;

/**
 * 按租户和订单号确认已收款金额，金额单位为分。
 */
@Value
public class ConfirmTradePaymentCmd implements Command {
    /**
     * 业务所属租户标识。
     */
    String tenantId;
    /**
     * 租户内唯一的业务订单号。
     */
    String orderNo;
    /**
     * 已确认的实付金额，单位为人民币分。
     */
    Long paidAmountCent;
}
