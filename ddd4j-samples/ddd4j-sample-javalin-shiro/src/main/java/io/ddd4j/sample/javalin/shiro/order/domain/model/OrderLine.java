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
package io.ddd4j.sample.javalin.shiro.order.domain.model;

import io.ddd4j.core.ddd.model.Entity;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.UUID;

/**
 * 订单行实体（Order 聚合内的实体）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class OrderLine implements Entity<String> {

    private static final long serialVersionUID = 1L;

    /**
     * 订单标识。
     */
    private final String id;
    /**
     * 商品标识。
     */
    private final String goodsId;
    /**
     * 商品名称。
     */
    private final String goodsName;
    /**
     * 商品单价。
     */
    private final Money unitPrice;
    /**
     * 数量。
     */
    private int quantity;

    /**
     * 创建订单行。
     *
     * @param id 订单标识
     * @param goodsId 商品标识
     * @param goodsName 商品名称
     * @param quantity 数量
     * @param unitPrice 商品单价
     */
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
     * 创建订单行。
     *
     * @param goodsId 商品标识
     * @param goodsName 商品名称
     * @param quantity 数量
     * @param unitPrice 商品单价
     * @return 订单行
     */
    public static OrderLine create(String goodsId, String goodsName, int quantity, Money unitPrice) {
        return new OrderLine(UUID.randomUUID().toString(), goodsId, goodsName, quantity, unitPrice);
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * 读取商品标识。
     *
     * @return 商品标识
     */
    public String goodsId() {
        return goodsId;
    }

    /**
     * 读取商品名称。
     *
     * @return 商品名称
     */
    public String goodsName() {
        return goodsName;
    }

    /**
     * 读取数量。
     *
     * @return 数量
     */
    public int quantity() {
        return quantity;
    }

    /**
     * 读取商品单价。
     *
     * @return 商品单价
     */
    public Money unitPrice() {
        return unitPrice;
    }

    /**
     * 计算订单行小计。
     *
     * @return 订单行小计
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    /**
     * 调整订单行数量。
     *
     * @param quantity 数量
     */
    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }
}
