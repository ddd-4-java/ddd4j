package io.ddd4j.sample.richmodel.order.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单行持久化对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLinePO {

    /**
     * 订单行 ID
     */
    private String id;
    /**
     * 商品 ID
     */
    private String productId;
    /**
     * 商品名称
     */
    private String productName;
    /**
     * 数量
     */
    private Integer quantity;
    /**
     * 单价
     */
    private BigDecimal unitPrice;
    /**
     * 货币代码
     */
    private String currency;
}
