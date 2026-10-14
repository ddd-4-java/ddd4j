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
import io.ddd4j.sample.order.domain.OrderStatus;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 订单读模型，Java 8 等价实现保留 record 值语义。
 */
@Value
public class OrderReadModel {
String id;
    String orderNo;
    String buyerId;
    String buyerName;
    OrderStatus status;
    BigDecimal totalAmount;

    /** Creates an order read model.
     * @param id order identifier
     * @param orderNo order number
     * @param buyerId buyer identifier
     * @param buyerName buyer name
     * @param status order status
     * @param totalAmount total amount
     */
    @JsonCreator
    public OrderReadModel(@JsonProperty("id") String id,
                          @JsonProperty("orderNo") String orderNo,
                          @JsonProperty("buyerId") String buyerId,
                          @JsonProperty("buyerName") String buyerName,
                          @JsonProperty("status") OrderStatus status,
                          @JsonProperty("totalAmount") BigDecimal totalAmount) {
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public String id() {
        return id;
    }

    public String orderNo() {
        return orderNo;
    }

    public String buyerId() {
        return buyerId;
    }

    public String buyerName() {
        return buyerName;
    }

    public OrderStatus status() {
        return status;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }
}
