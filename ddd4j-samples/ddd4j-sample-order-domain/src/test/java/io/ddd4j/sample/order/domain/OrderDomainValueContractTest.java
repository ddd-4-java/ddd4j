package io.ddd4j.sample.order.domain;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDomainValueContractTest {

    @Test
    void moneyRetainsNormalizedValueAndBothReaderForms() {
        Money money = new Money(new BigDecimal("12.345"), " cny ");
        assertEquals(new BigDecimal("12.35"), money.getAmount());
        assertEquals(money.getAmount(), money.amount());
        assertEquals("CNY", money.getCurrency());
        assertEquals(money.getCurrency(), money.currency());
        Money same = new Money(new BigDecimal("12.35"), "CNY");
        assertEquals(same, money);
        assertEquals(same.hashCode(), money.hashCode());
        assertTrue(money.toString().startsWith("Money("));
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("-1"), "CNY"));
    }

    @Test
    void orderQueryRetainsValueAndBothReaderForms() {
        OrderQuery query = new OrderQuery("buyer-1", OrderStatus.DRAFT, 1, 20);
        assertEquals(query.getBuyerId(), query.buyerId());
        assertEquals(query.getStatus(), query.status());
        assertEquals(query.getPage(), query.page());
        assertEquals(query.getSize(), query.size());
        OrderQuery same = new OrderQuery("buyer-1", OrderStatus.DRAFT, 1, 20);
        assertEquals(same, query);
        assertEquals(same.hashCode(), query.hashCode());
        assertTrue(query.toString().startsWith("OrderQuery("));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderQuery("buyer-1", OrderStatus.DRAFT, 0, 20));
    }

    @Test
    void moneyAndOrderQueryRoundTripThroughJson() {
        Money money = new Money(new BigDecimal("12.345"), " cny ");
        String moneyJson = JsonKit.toJson(money);
        assertEquals(money, JsonKit.toObject(moneyJson, Money.class));

        OrderQuery query = new OrderQuery("buyer-1", OrderStatus.DRAFT, 1, 20);
        String queryJson = JsonKit.toJson(query);
        assertEquals(query, JsonKit.toObject(queryJson, OrderQuery.class));
    }
}
