package io.ddd4j.sample.richmodel.order.application;

import java.math.BigDecimal;

/**
 * Command for adding an order line.
 *
 * @param orderId order id
 * @param productId product id
 * @param productName product name
 * @param quantity quantity
 * @param unitPrice unit price
 */
public record AddOrderLineCommand(String orderId, String productId, String productName, int quantity, BigDecimal unitPrice) {
}
