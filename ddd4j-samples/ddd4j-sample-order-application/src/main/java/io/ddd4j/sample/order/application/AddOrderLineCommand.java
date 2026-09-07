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

import lombok.Value;

import java.math.BigDecimal;

/** 添加订单行命令，Java 8 等价实现保留 record 值语义。 */
@Value
public class AddOrderLineCommand {
    String orderId;
    String goodsId;
    String goodsName;
    int quantity;
    BigDecimal unitPrice;

    public String orderId() { return orderId; }
    public String goodsId() { return goodsId; }
    public String goodsName() { return goodsName; }
    public int quantity() { return quantity; }
    public BigDecimal unitPrice() { return unitPrice; }
}
