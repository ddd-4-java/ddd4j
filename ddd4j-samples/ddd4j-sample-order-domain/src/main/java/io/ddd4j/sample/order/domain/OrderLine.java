package io.ddd4j.sample.order.domain;

import io.ddd4j.core.ddd.model.Entity;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.UUID;

public final class OrderLine implements Entity<String> {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String goodsId;
    private final String goodsName;
    private final Money unitPrice;
    private int quantity;

    public OrderLine(String id, String goodsId, String goodsName, int quantity, Money unitPrice) {
        if (StrKit.isBlank(id) || StrKit.isBlank(goodsId) || StrKit.isBlank(goodsName)) {
            throw new IllegalArgumentException("order line identifiers and name must not be blank");
        }
        this.id = id;
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        changeQuantity(quantity);
    }

    public static OrderLine create(String goodsId, String goodsName, int quantity, Money unitPrice) {
        return new OrderLine(UUID.randomUUID().toString(), goodsId, goodsName, quantity, unitPrice);
    }

    @Override
    public String id() {
        return id;
    }

    public String goodsId() {
        return goodsId;
    }

    public String goodsName() {
        return goodsName;
    }

    public int quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }
}
