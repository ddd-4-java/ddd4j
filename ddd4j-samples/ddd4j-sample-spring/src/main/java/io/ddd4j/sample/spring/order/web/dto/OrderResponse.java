package io.ddd4j.sample.spring.order.web.dto;

import io.ddd4j.sample.spring.order.domain.model.Money;
import io.ddd4j.sample.spring.order.domain.model.Order;
import io.ddd4j.sample.spring.order.domain.model.OrderLine;
import io.ddd4j.sample.spring.order.domain.model.OrderStatus;

import java.util.List;

/**
 * 订单 REST 响应。
 *
 * @param id          订单 ID
 * @param orderNo     订单编号
 * @param buyerId     买家 ID
 * @param buyerName   买家名称
 * @param status      订单状态
 * @param totalAmount 总金额数值
 * @param currency    货币代码
 * @param lines       订单行列表
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record OrderResponse(String id, String orderNo, String buyerId, String buyerName,
                            OrderStatus status, String totalAmount, String currency,
                            List<OrderLineResponse> lines) {

    /**
     * 从订单聚合转换为响应 DTO。
     *
     * @param order 订单聚合
     * @return REST 响应 DTO
     */
    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = order.lines().stream()
                .map(OrderLineResponse::from)
                .toList();
        return new OrderResponse(
                order.id(),
                order.orderNo(),
                order.buyerId(),
                order.buyerName(),
                order.status(),
                total.amount().toPlainString(),
                total.currency(),
                lineResponses
        );
    }

    /**
     * 订单行响应。
     */
    public record OrderLineResponse(String id, String goodsId, String goodsName,
                                    int quantity, String unitPrice, String currency) {

        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(
                    line.id(),
                    line.goodsId(),
                    line.goodsName(),
                    line.quantity(),
                    line.unitPrice().amount().toPlainString(),
                    line.unitPrice().currency()
            );
        }
    }
}
