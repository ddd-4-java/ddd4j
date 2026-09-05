package io.ddd4j.sample.quarkus.cqrs.order.web.dto;

import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Money;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.OrderLine;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** 稳定的订单 HTTP 表示，避免领域模型依赖 Jackson。 */
public record OrderResponse(String id, String orderNo, String buyerId, String buyerName,
                            OrderStatus status, BigDecimal totalAmount, String currency,
                            List<OrderLineResponse> lines) {

    public static OrderResponse from(Order order) {
        Order source = Objects.requireNonNull(order, "order must not be null");
        Money total = source.totalAmount();
        return new OrderResponse(source.id(), source.orderNo(), source.buyerId(), source.buyerName(),
                source.status(), total.amount(), total.currency(), source.lines().stream()
                .map(OrderLineResponse::from)
                .toList());
    }

    /** 订单行的稳定 HTTP 表示。 */
    public record OrderLineResponse(String id, String goodsId, String goodsName, int quantity,
                                    BigDecimal unitPrice, String currency, BigDecimal subtotal) {

        private static OrderLineResponse from(OrderLine line) {
            Money unitPrice = line.unitPrice();
            return new OrderLineResponse(line.id(), line.goodsId(), line.goodsName(), line.quantity(),
                    unitPrice.amount(), unitPrice.currency(), line.subtotal().amount());
        }
    }
}
