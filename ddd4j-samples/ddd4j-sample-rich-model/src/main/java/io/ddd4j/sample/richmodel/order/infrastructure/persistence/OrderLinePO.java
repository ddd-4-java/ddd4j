package io.ddd4j.sample.richmodel.order.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Persistence object for order line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLinePO {

    private String id;
    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String currency;
}
