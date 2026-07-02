package io.ddd4j.sample.richmodel.order.application;

/**
 * Command for creating a draft order.
 *
 * @param orderNo order number
 * @param buyerId buyer id
 * @param buyerName buyer display name
 */
public record CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
}
