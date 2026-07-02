package io.ddd4j.sample.richmodel.order.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Persistence object for order aggregate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPO {

    private String id;
    private String orderNo;
    private String buyerId;
    private String buyerName;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private List<OrderLinePO> lines;
}
