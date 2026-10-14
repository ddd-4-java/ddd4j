package io.ddd4j.sample.javalin.cqrs.order;

import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService.AddOrderLineCommand;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService.CreateOrderCommand;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderValueAccessorsTest {

    @Test
    void orderCommandsRetainComponentAndBeanAccess() {
        CreateOrderCommand create = new CreateOrderCommand("order-1", "buyer-1", "Buyer");
        assertEquals(create.orderNo(), create.getOrderNo());
        assertEquals(create.buyerId(), create.getBuyerId());
        assertEquals(create.buyerName(), create.getBuyerName());

        AddOrderLineCommand add = new AddOrderLineCommand(
                "order-1", "goods-1", "Goods", 2, new BigDecimal("12.50"));
        assertEquals(add.orderId(), add.getOrderId());
        assertEquals(add.goodsId(), add.getGoodsId());
        assertEquals(add.goodsName(), add.getGoodsName());
        assertEquals(add.quantity(), add.getQuantity());
        assertEquals(add.unitPrice(), add.getUnitPrice());
    }

    @Test
    void moneyRetainsComponentAndBeanAccess() {
        Money money = new Money(new BigDecimal("12.345"), " cny ");
        assertEquals(money.amount(), money.getAmount());
        assertEquals(money.currency(), money.getCurrency());
    }
}
