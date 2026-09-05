package io.ddd4j.sample.javalin.shiro.order.web.dto;

import java.util.Objects;
/**
 * 创建订单 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */public final class CreateOrderRequest {
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;

        public CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateOrderRequest other = (CreateOrderRequest) o;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderNo, buyerId, buyerName); }
        @Override
        public String toString() {
            return "CreateOrderRequest{" + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + "}";
        }
    }