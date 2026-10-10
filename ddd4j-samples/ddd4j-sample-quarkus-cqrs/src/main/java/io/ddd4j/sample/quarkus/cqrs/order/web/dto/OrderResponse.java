package io.ddd4j.sample.quarkus.cqrs.order.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Money;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.OrderLine;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 稳定的订单 HTTP 表示，避免领域模型依赖 Jackson。
 */
public final class OrderResponse {

    private static final long serialVersionUID = 0L;

    private final String id;

    private final String orderNo;

    private final String buyerId;

    private final String buyerName;

    private final OrderStatus status;

    private final BigDecimal totalAmount;

    private final String currency;

    private final List<OrderLineResponse> lines;

    public static OrderResponse from(Order order) {
        Order source = Objects.requireNonNull(order, "order must not be null");
        Money total = source.totalAmount();
        return new OrderResponse(source.id(), source.orderNo(), source.buyerId(), source.buyerName(), source.status(), total.amount(), total.currency(), source.lines().stream().map(OrderLineResponse::from).toList());
    }

    /**
     * 订单行的稳定 HTTP 表示。
     */
    public final static class OrderLineResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String goodsId;

        private final String goodsName;

        private final int quantity;

        private final BigDecimal unitPrice;

        private final String currency;

        private final BigDecimal subtotal;

        private static OrderLineResponse from(OrderLine line) {
            Money unitPrice = line.unitPrice();
            return new OrderLineResponse(line.id(), line.goodsId(), line.goodsName(), line.quantity(), unitPrice.amount(), unitPrice.currency(), line.subtotal().amount());
        }

        @JsonCreator()
        public OrderLineResponse(@JsonProperty("id") String id, @JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice, @JsonProperty("currency") String currency, @JsonProperty("subtotal") BigDecimal subtotal) {
            this.id = id;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.currency = currency;
            this.subtotal = subtotal;
        }

        @JsonProperty("id")
        public String id() {
            return id;
        }

        @JsonProperty("goodsId")
        public String goodsId() {
            return goodsId;
        }

        @JsonProperty("goodsName")
        public String goodsName() {
            return goodsName;
        }

        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        @JsonProperty("unitPrice")
        public BigDecimal unitPrice() {
            return unitPrice;
        }

        @JsonProperty("currency")
        public String currency() {
            return currency;
        }

        @JsonProperty("subtotal")
        public BigDecimal subtotal() {
            return subtotal;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            OrderLineResponse other = (OrderLineResponse) obj;
            return Objects.equals(this.id, other.id) && Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && this.quantity == other.quantity && Objects.equals(this.unitPrice, other.unitPrice) && Objects.equals(this.currency, other.currency) && Objects.equals(this.subtotal, other.subtotal);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(goodsId);
            result = 31 * result + Objects.hashCode(goodsName);
            result = 31 * result + Integer.hashCode(quantity);
            result = 31 * result + Objects.hashCode(unitPrice);
            result = 31 * result + Objects.hashCode(currency);
            result = 31 * result + Objects.hashCode(subtotal);
            return result;
        }

        @Override
        public String toString() {
            return "OrderLineResponse[id=" + id + ", goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + ", currency=" + currency + ", subtotal=" + subtotal + "]";
        }
    }

    @JsonCreator()
    public OrderResponse(@JsonProperty("id") String id, @JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName, @JsonProperty("status") OrderStatus status, @JsonProperty("totalAmount") BigDecimal totalAmount, @JsonProperty("currency") String currency, @JsonProperty("lines") List<OrderLineResponse> lines) {
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lines = lines;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("orderNo")
    public String orderNo() {
        return orderNo;
    }

    @JsonProperty("buyerId")
    public String buyerId() {
        return buyerId;
    }

    @JsonProperty("buyerName")
    public String buyerName() {
        return buyerName;
    }

    @JsonProperty("status")
    public OrderStatus status() {
        return status;
    }

    @JsonProperty("totalAmount")
    public BigDecimal totalAmount() {
        return totalAmount;
    }

    @JsonProperty("currency")
    public String currency() {
        return currency;
    }

    @JsonProperty("lines")
    public List<OrderLineResponse> lines() {
        return lines;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        OrderResponse other = (OrderResponse) obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && Objects.equals(this.currency, other.currency) && Objects.equals(this.lines, other.lines);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(orderNo);
        result = 31 * result + Objects.hashCode(buyerId);
        result = 31 * result + Objects.hashCode(buyerName);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(totalAmount);
        result = 31 * result + Objects.hashCode(currency);
        result = 31 * result + Objects.hashCode(lines);
        return result;
    }

    @Override
    public String toString() {
        return "OrderResponse[id=" + id + ", orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + ", status=" + status + ", totalAmount=" + totalAmount + ", currency=" + currency + ", lines=" + lines + "]";
    }
}
