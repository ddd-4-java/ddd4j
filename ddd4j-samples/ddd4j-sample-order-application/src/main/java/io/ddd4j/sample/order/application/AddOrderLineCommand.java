package io.ddd4j.sample.order.application;

import java.math.BigDecimal;

public record AddOrderLineCommand(String orderId, String goodsId, String goodsName,
                                  int quantity, BigDecimal unitPrice) {
}
