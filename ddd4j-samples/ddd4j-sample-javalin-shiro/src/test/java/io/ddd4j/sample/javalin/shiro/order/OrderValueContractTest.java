package io.ddd4j.sample.javalin.shiro.order;

import io.ddd4j.sample.javalin.shiro.order.application.AddOrderLineCommand;
import io.ddd4j.sample.javalin.shiro.order.application.CreateOrderCommand;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.web.OrderResource;
import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderValueContractTest {

    @Test
    void commandsRetainBeanAndComponentReaders() {
        CreateOrderCommand create = new CreateOrderCommand("O-1", "B-1", "Alice");
        assertEquals(create.getOrderNo(), create.orderNo());
        assertEquals(create.getBuyerId(), create.buyerId());
        assertEquals(create.getBuyerName(), create.buyerName());
        assertEquals(new CreateOrderCommand("O-1", "B-1", "Alice"), create);
        assertTrue(create.toString().startsWith("CreateOrderCommand("));

        AddOrderLineCommand add = new AddOrderLineCommand("id-1", "G-1", "Coffee", 2,
                new BigDecimal("12.50"));
        assertEquals(add.getOrderId(), add.orderId());
        assertEquals(add.getGoodsId(), add.goodsId());
        assertEquals(add.getGoodsName(), add.goodsName());
        assertEquals(add.getQuantity(), add.quantity());
        assertEquals(add.getUnitPrice(), add.unitPrice());
        assertEquals(new AddOrderLineCommand("id-1", "G-1", "Coffee", 2,
                new BigDecimal("12.50")), add);
        assertTrue(add.toString().startsWith("AddOrderLineCommand("));
    }

    @Test
    void moneyRetainsNormalizedValueAndDiscountContract() {
        Money money = new Money(new BigDecimal("12.345"), " cny ");
        assertEquals(new BigDecimal("12.35"), money.getAmount());
        assertEquals(money.getAmount(), money.amount());
        assertEquals("CNY", money.getCurrency());
        assertEquals(money.getCurrency(), money.currency());
        assertEquals(new Money(new BigDecimal("12.35"), "CNY"), money);
        assertEquals(new BigDecimal("11.12"), money.discount(10).amount());
        assertTrue(money.toString().startsWith("Money("));
    }

    @Test
    void moneyRetainsThreeLineValueHash() {
        Money money = new Money(new BigDecimal("12.50"), "CNY");
        int expected = 1;
        expected = 59 * expected + money.amount().hashCode();
        expected = 59 * expected + money.currency().hashCode();

        assertEquals(expected, money.hashCode());
    }

    @Test
    void moneyRoundTripsThroughJson() {
        Money money = new Money(new BigDecimal("12.345"), " cny ");
        assertEquals(money, JsonKit.toObject(JsonKit.toJson(money), Money.class));
    }

    @Test
    void discountViewRetainsReadOnlyValueContract() {
        OrderResource.DiscountView view = new OrderResource.DiscountView("11.12", "CNY");
        assertEquals(view.getAmount(), view.amount());
        assertEquals(view.getCurrency(), view.currency());
        assertEquals(new OrderResource.DiscountView("11.12", "CNY"), view);
        assertEquals(-1536171204, view.hashCode());
        assertEquals(view, JsonKit.toObject(JsonKit.toJson(view), OrderResource.DiscountView.class));
        assertTrue(view.toString().contains("DiscountView(amount="));
    }
}
