package io.ddd4j.sample.javalin.shiro.order.web.dto;

import java.util.stream.Collectors;
import java.util.Objects;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Order;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderStatus;

import java.util.List;

/**
 * 订单 REST 响应。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */public final class OrderResponse {
        private final String id;
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;
        private final OrderStatus status;
        private final String totalAmount;
        private final String currency;
        private final List<OrderLineResponse> lines;

        public OrderResponse(String id, String orderNo, String buyerId, String buyerName, OrderStatus status, String totalAmount, String currency, List<OrderLineResponse> lines) {
            this.id = id;
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.status = status;
            this.totalAmount = totalAmount;
            this.currency = currency;
            this.lines = lines;
        }
        public String id() { return id; }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        public OrderStatus status() { return status; }
        public String totalAmount() { return totalAmount; }
        public String currency() { return currency; }
        public List<OrderLineResponse> lines() { return lines; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OrderResponse other = (OrderResponse) o;
            return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && Objects.equals(this.currency, other.currency) && Objects.equals(this.lines, other.lines);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(id, orderNo, buyerId, buyerName, status, totalAmount, currency, lines); }
        @Override
        public String toString() {
            return "OrderResponse{" + "id=" + id + ", " + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + ", " + "status=" + status + ", " + "totalAmount=" + totalAmount + ", " + "currency=" + currency + ", " + "lines=" + lines + "}";
        }
    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = order.lines().stream()
                .map(OrderLineResponse::from)
                .collect(java.util.stream.Collectors.toList());
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getBuyerId(),
                order.getBuyerName(),
                order.getStatus(),
                total.getAmount().toPlainString(),
                total.getCurrency(),
                lineResponses
        );
    }public final class OrderLineResponse {
        private final String id;
        private final String goodsId;
        private final String goodsName;
        private final int quantity;
        private final String unitPrice;
        private final String currency;

        public OrderLineResponse(String id, String goodsId, String goodsName, int quantity, String unitPrice, String currency) {
            this.id = id;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.currency = currency;
        }
        public String id() { return id; }
        public String goodsId() { return goodsId; }
        public String goodsName() { return goodsName; }
        public int quantity() { return quantity; }
        public String unitPrice() { return unitPrice; }
        public String currency() { return currency; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OrderLineResponse other = (OrderLineResponse) o;
            return Objects.equals(this.id, other.id) && Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && Objects.equals(this.quantity, other.quantity) && Objects.equals(this.unitPrice, other.unitPrice) && Objects.equals(this.currency, other.currency);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(id, goodsId, goodsName, quantity, unitPrice, currency); }
        @Override
        public String toString() {
            return "OrderLineResponse{" + "id=" + id + ", " + "goodsId=" + goodsId + ", " + "goodsName=" + goodsName + ", " + "quantity=" + quantity + ", " + "unitPrice=" + unitPrice + ", " + "currency=" + currency + "}";
        }
        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(
                    line.getId(),
                    line.goodsId(),
                    line.goodsName(),
                    line.quantity(),
                    line.unitPrice().getAmount().toPlainString(),
                    line.unitPrice().getCurrency()
            );
        }
    
    }
    }