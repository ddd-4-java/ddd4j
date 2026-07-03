package io.ddd4j.sample.richmodel.order.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单持久化对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPO {

    /**
     * 订单 ID
     */
    private String id;
    /**
     * 订单编号
     */
    private String orderNo;
    /**
     * 买家 ID
     */
    private String buyerId;
    /**
     * 买家名称
     */
    private String buyerName;
    /**
     * 订单状态
     */
    private String status;
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 货币代码
     */
    private String currency;
    /**
     * 订单行列表
     */
    private List<OrderLinePO> lines;
}
