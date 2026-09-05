package io.ddd4j.sample.order.application;

import java.util.Objects;
import io.ddd4j.sample.order.domain.OrderStatus;

import java.math.BigDecimal;

public final class OrderReadModel {
        private final String id;
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;
        private final OrderStatus status;
        private final BigDecimal totalAmount;

        public OrderReadModel(String id, String orderNo, String buyerId, String buyerName, OrderStatus status, BigDecimal totalAmount) {
            this.id = id;
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.status = status;
            this.totalAmount = totalAmount;
        }
        public String id() { return id; }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        public OrderStatus status() { return status; }
        public BigDecimal totalAmount() { return totalAmount; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OrderReadModel other = (OrderReadModel) o;
            return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(id, orderNo, buyerId, buyerName, status, totalAmount); }
        @Override
        public String toString() {
            return "OrderReadModel{" + "id=" + id + ", " + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + ", " + "status=" + status + ", " + "totalAmount=" + totalAmount + "}";
        }
    }
