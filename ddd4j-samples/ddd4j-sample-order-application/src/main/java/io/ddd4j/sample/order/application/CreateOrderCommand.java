package io.ddd4j.sample.order.application;

public record CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
}
