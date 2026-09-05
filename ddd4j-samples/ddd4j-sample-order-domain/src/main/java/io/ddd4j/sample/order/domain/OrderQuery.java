package io.ddd4j.sample.order.domain;

public record OrderQuery(String buyerId, OrderStatus status, int page, int size) {
    public OrderQuery {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
    }
}
