package io.ddd4j.sample.richmodel.order.domain.model;

import io.ddd4j.core.domain.Entity;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.UUID;

/**
 * Entity inside the Order aggregate.
 */
public final class OrderLine implements Entity<String> {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String productId;
    private final String productName;
    private final Money unitPrice;
    private int quantity;

    public OrderLine(String id, String productId, String productName, int quantity, Money unitPrice) {
        if (StrKit.isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (StrKit.isBlank(productId)) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (StrKit.isBlank(productName)) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        changeQuantity(quantity);
    }

    public static OrderLine create(String productId, String productName, int quantity, Money unitPrice) {
        return new OrderLine(UUID.randomUUID().toString(), productId, productName, quantity, unitPrice);
    }

    @Override
    public String id() {
        return id;
    }

    public String productId() {
        return productId;
    }

    public String productName() {
        return productName;
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
