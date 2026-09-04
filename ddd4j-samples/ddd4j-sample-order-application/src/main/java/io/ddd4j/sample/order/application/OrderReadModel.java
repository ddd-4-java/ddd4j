package io.ddd4j.sample.order.application;

import io.ddd4j.sample.order.domain.OrderStatus;

import java.math.BigDecimal;

public record OrderReadModel(String id, String orderNo, String buyerId, String buyerName,
                             OrderStatus status, BigDecimal totalAmount) {
}
