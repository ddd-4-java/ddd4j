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

import io.ddd4j.sample.order.domain.OrderStatus;
import lombok.Value;

import java.math.BigDecimal;

/** 订单读模型，Java 8 等价实现保留 record 值语义。 */
@Value
public class OrderReadModel {
    String id;
    String orderNo;
    String buyerId;
    String buyerName;
    OrderStatus status;
    BigDecimal totalAmount;

    public String id() { return id; }
    public String orderNo() { return orderNo; }
    public String buyerId() { return buyerId; }
    public String buyerName() { return buyerName; }
    public OrderStatus status() { return status; }
    public BigDecimal totalAmount() { return totalAmount; }
}
