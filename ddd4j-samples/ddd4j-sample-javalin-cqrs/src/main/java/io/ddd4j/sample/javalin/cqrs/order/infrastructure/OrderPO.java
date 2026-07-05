package io.ddd4j.sample.javalin.cqrs.order.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单持久化对象（PO）。
 *
 * <p>PO 是基础设施关注点：{@link io.ddd4j.sample.javalin.cqrs.order.domain.model.Order}
 * 充血聚合不直接包含 PO 字段，而是通过 {@link InMemoryOrderRepository}
 * 实现 {@link io.ddd4j.core.ddd.model.DomainObjectMapper} 完成双向转换。
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
     * 订单状态（字符串）
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
