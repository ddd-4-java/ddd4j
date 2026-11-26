package io.ddd4j.sample.javalin;

import io.ddd4j.sample.javalin.goods.web.GoodsController.CreateGoodsRequest;
import io.ddd4j.sample.javalin.goods.web.GoodsController.UpdateGoodsRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(create.toString().contains("CreateGoodsRequest("));

        CreateGoodsRequest derived = new CreateGoodsRequest() {
        };
        derived.setCode("SKU-1");
        derived.setName("Coffee");
        derived.setPrice(new BigDecimal("12.50"));
        derived.setStock(3);
        assertEquals(create, derived);
        assertEquals(derived, create);
        assertEquals(create.hashCode(), derived.hashCode());

        UpdateGoodsRequest update = new UpdateGoodsRequest();
        update.setName("Coffee");
        update.setPrice(new BigDecimal("13.50"));
        assertEquals(update.getName(), update.name());
        assertEquals(update.getPrice(), update.price());
        UpdateGoodsRequest sameUpdate = new UpdateGoodsRequest("Coffee", new BigDecimal("13.50"));
        assertEquals(sameUpdate, update);
        assertEquals(sameUpdate.hashCode(), update.hashCode());
        assertFalse(Modifier.isFinal(UpdateGoodsRequest.class.getModifiers()));
        assertTrue(update.toString().contains("UpdateGoodsRequest("));
    }
}
