package io.ddd4j.sample.order.domain;

import java.util.Objects;

public final class OrderQuery {
    private final String buyerId;
    private final OrderStatus status;
    private final int page;
    private final int size;

    public OrderQuery(String buyerId, OrderStatus status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
        this.buyerId = buyerId;
        this.status = status;
        this.page = page;
        this.size = size;
    }

    public String buyerId() { return buyerId; }
    public OrderStatus status() { return status; }
    public int page() { return page; }
    public int size() { return size; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderQuery other = (OrderQuery) o;
        return Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.status, other.status) && Objects.equals(this.page, other.page) && Objects.equals(this.size, other.size);
    }

    @Override
    public int hashCode() { return Objects.hash(buyerId, status, page, size); }

    @Override
    public String toString() {
        return "OrderQuery{" + "buyerId=" + buyerId + ", " + "status=" + status + ", " + "page=" + page + ", " + "size=" + size + "}";
    }
}
