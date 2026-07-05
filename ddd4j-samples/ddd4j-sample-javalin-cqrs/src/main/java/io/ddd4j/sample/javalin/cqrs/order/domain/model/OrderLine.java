package io.ddd4j.sample.javalin.cqrs.order.domain.model;

import io.ddd4j.core.ddd.model.Entity;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.UUID;

/**
 * 订单行实体（Order 聚合内的实体）。
 *
 * <p>作为订单聚合的一部分，封装商品购买数量和价格信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class OrderLine implements Entity<String> {

    private static final long serialVersionUID = 1L;

    /**
     * 订单行 ID
     */
    private final String id;
    /**
     * 商品 ID
     */
    private final String goodsId;
    /**
     * 商品名称
     */
    private final String goodsName;
    /**
     * 单价
     */
    private final Money unitPrice;
    /**
     * 数量
     */
    private int quantity;

    public OrderLine(String id, String goodsId, String goodsName, int quantity, Money unitPrice) {
        if (StrKit.isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (StrKit.isBlank(goodsId)) {
            throw new IllegalArgumentException("goodsId must not be blank");
        }
        if (StrKit.isBlank(goodsName)) {
            throw new IllegalArgumentException("goodsName must not be blank");
        }
        this.id = id;
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        changeQuantity(quantity);
    }

    /**
     * 创建新的订单行。
     *
     * @param goodsId   商品 ID
     * @param goodsName 商品名称
     * @param quantity    数量
     * @param unitPrice   单价
     * @return 订单行实例
     */
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

    /**
     * 计算小计金额。
     *
     * @return 小计金额
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    /**
     * 修改数量。
     *
     * @param quantity 新数量
     * @throws IllegalArgumentException 如果数量不大于 0
     */
    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }
}
