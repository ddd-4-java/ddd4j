package io.ddd4j.sample.javalin;

import io.ddd4j.sample.javalin.goods.web.GoodsController.CreateGoodsRequest;
import io.ddd4j.sample.javalin.goods.web.GoodsController.UpdateGoodsRequest;
import io.ddd4j.sample.javalin.order.web.OrderController.AddOrderLineRequest;
import io.ddd4j.sample.javalin.order.web.OrderController.CreateOrderRequest;
import io.ddd4j.sample.javalin.order.web.OrderController.OrderResponse;
import io.ddd4j.sample.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlainJavalinDtoContractTest {

    @Test
    void goodsRequestsSupportBeanAndComponentAccess() {
        CreateGoodsRequest create = new CreateGoodsRequest();
        create.setCode("SKU-1");
        create.setName("Coffee");
        create.setPrice(new BigDecimal("12.50"));
        create.setStock(3);
        assertEquals(create.getCode(), create.code());
        assertEquals(create.getName(), create.name());
        assertEquals(create.getPrice(), create.price());
        assertEquals(create.getStock(), create.stock());
        CreateGoodsRequest same = new CreateGoodsRequest("SKU-1", "Coffee", new BigDecimal("12.50"), 3);
        assertEquals(same, create);
        assertEquals(same.hashCode(), create.hashCode());
        assertFalse(Modifier.isFinal(CreateGoodsRequest.class.getModifiers()));

        UpdateGoodsRequest update = new UpdateGoodsRequest();
        update.setName("Coffee");
        update.setPrice(new BigDecimal("13.50"));
        assertEquals(update.getName(), update.name());
        assertEquals(update.getPrice(), update.price());
        UpdateGoodsRequest sameUpdate = new UpdateGoodsRequest("Coffee", new BigDecimal("13.50"));
        assertEquals(sameUpdate, update);
        assertEquals(sameUpdate.hashCode(), update.hashCode());
        assertFalse(Modifier.isFinal(UpdateGoodsRequest.class.getModifiers()));
    }

    @Test
    void orderRequestsSupportBeanAndComponentAccess() {
        CreateOrderRequest create = new CreateOrderRequest();
        create.setOrderNo("O-1");
        create.setBuyerId("B-1");
        create.setBuyerName("Alice");
        assertEquals(create.getOrderNo(), create.orderNo());
        assertEquals(create.getBuyerId(), create.buyerId());
        assertEquals(create.getBuyerName(), create.buyerName());
        CreateOrderRequest same = new CreateOrderRequest("O-1", "B-1", "Alice");
        assertEquals(same, create);
        assertEquals(same.hashCode(), create.hashCode());
        assertFalse(Modifier.isFinal(CreateOrderRequest.class.getModifiers()));

        AddOrderLineRequest line = new AddOrderLineRequest();
        line.setGoodsId("G-1");
        line.setGoodsName("Coffee");
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("12.50"));
        assertEquals(line.getGoodsId(), line.goodsId());
        assertEquals(line.getGoodsName(), line.goodsName());
        assertEquals(line.getQuantity(), line.quantity());
        assertEquals(line.getUnitPrice(), line.unitPrice());
        AddOrderLineRequest sameLine = new AddOrderLineRequest("G-1", "Coffee", 2,
                new BigDecimal("12.50"));
        assertEquals(sameLine, line);
        assertEquals(sameLine.hashCode(), line.hashCode());
        assertFalse(Modifier.isFinal(AddOrderLineRequest.class.getModifiers()));
    }

    @Test
    void orderResponseRetainsBeanAndComponentReadAccess() {
        OrderResponse response = new OrderResponse("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, new BigDecimal("25.00"), "CNY", 2);
        assertEquals(response.getId(), response.id());
        assertEquals(response.getOrderNo(), response.orderNo());
        assertEquals(response.getBuyerId(), response.buyerId());
        assertEquals(response.getBuyerName(), response.buyerName());
        assertEquals(response.getStatus(), response.status());
        assertEquals(response.getTotalAmount(), response.totalAmount());
        assertEquals(response.getCurrency(), response.currency());
        assertEquals(response.getLineCount(), response.lineCount());
        OrderResponse same = new OrderResponse("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, new BigDecimal("25.00"), "CNY", 2);
        assertEquals(same, response);
        assertEquals(same.hashCode(), response.hashCode());
        assertFalse(Modifier.isFinal(OrderResponse.class.getModifiers()));
    }
}
