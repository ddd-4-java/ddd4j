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
package io.ddd4j.sample.order.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 添加订单行命令，Java 8 等价实现保留 record 值语义。
 */
@Value
public class AddOrderLineCommand {
String orderId;
    String goodsId;
    String goodsName;
    int quantity;
    BigDecimal unitPrice;

    /** Creates an order-line command.
     * @param orderId order identifier
     * @param goodsId goods identifier
     * @param goodsName goods name
     * @param quantity quantity
     * @param unitPrice unit price
     */
    @JsonCreator
    public AddOrderLineCommand(@JsonProperty("orderId") String orderId,
                               @JsonProperty("goodsId") String goodsId,
                               @JsonProperty("goodsName") String goodsName,
                               @JsonProperty("quantity") int quantity,
                               @JsonProperty("unitPrice") BigDecimal unitPrice) {
        this.orderId = orderId;
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String orderId() {
        return orderId;
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

    public BigDecimal unitPrice() {
        return unitPrice;
    }
}
