/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.order.domain;

import io.ddd4j.core.ddd.model.Entity;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.UUID;

/** 包含商品、单价和数量的订单明细实体。 */
public final class OrderLine implements Entity<String> {

    private static final long serialVersionUID = 1L;

    /** 明细标识。 */
    private final String id;
    /** 商品标识。 */
    private final String goodsId;
    /** 商品名称。 */
    private final String goodsName;
    /** 商品单价。 */
    private final Money unitPrice;
    /** 商品数量。 */
    private int quantity;

    /**
     * 重建订单明细。
     *
     * @param id 明细标识
     * @param goodsId 商品标识
     * @param goodsName 商品名称
     * @param quantity 正数数量
     * @param unitPrice 商品单价
     */
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

    /**
     * @param goodsId 商品标识
     * @param goodsName 商品名称
     * @param quantity 正数数量
     * @param unitPrice 商品单价
     * @return 新的订单明细
     */
    public static OrderLine create(String goodsId, String goodsName, int quantity, Money unitPrice) {
        return new OrderLine(UUID.randomUUID().toString(), goodsId, goodsName, quantity, unitPrice);
    }

    @Override
    public String id() {
        return id;
    }

    /** @return 商品标识 */
    public String goodsId() {
        return goodsId;
    }

    /** @return 商品名称 */
    public String goodsName() {
        return goodsName;
    }

    /** @return 商品数量 */
    public int quantity() {
        return quantity;
    }

    /** @return 商品单价 */
    public Money unitPrice() {
        return unitPrice;
    }

    /** @return 单价乘以数量的小计 */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    /** @param quantity 新的正数数量 */
    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }
}
