package io.ddd4j.sample.order.client.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.command.Command;
import lombok.Data;
import java.util.List;

/** 按选定的规格与购买数量下单。 */
@Data
public class PlaceTradeOrderCmd implements Command {
    /** \u7531 HTTP \u8def\u5f84\u63d0\u4f9b\u7684\u8ba2\u5355\u6240\u5c5e\u79df\u6237\u3002 */
    @JsonIgnore
    private String tenantId;
    /** \u7531 HTTP \u8def\u5f84\u63d0\u4f9b\u7684\u79df\u6237\u5185\u8ba2\u5355\u53f7\u3002 */
    @JsonIgnore
    private String orderNo;
    /** \u4ea4\u6613\u6216\u5546\u54c1\u9002\u7528\u7684\u95e8\u5e97\u6807\u8bc6\u3002 */
    private Long shopId;
    /** \u672c\u6b21\u4e0b\u5355\u8bf7\u6c42\u7684\u89c4\u683c\u4e0e\u8d2d\u4e70\u4ef6\u6570\u3002 */
    private List<TradeOrderItemCmd> items;
}
