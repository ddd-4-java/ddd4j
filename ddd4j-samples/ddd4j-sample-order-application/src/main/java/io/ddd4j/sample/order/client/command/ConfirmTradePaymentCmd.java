package io.ddd4j.sample.order.client.command;

import io.ddd4j.core.cqrs.command.Command;
import lombok.Value;

/** 按租户和订单号确认已收款金额，金额单位为分。 */
@Value
public class ConfirmTradePaymentCmd implements Command {
    /** \u4e1a\u52a1\u6240\u5c5e\u79df\u6237\u6807\u8bc6\u3002 */
    String tenantId;
    /** \u79df\u6237\u5185\u552f\u4e00\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002 */
    String orderNo;
    /** \u5df2\u786e\u8ba4\u7684\u5b9e\u4ed8\u91d1\u989d\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206\u3002 */
    Long paidAmountCent;
}
