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

import lombok.Value;

/** 订单查询条件，Java 8 等价实现保留 record 的值语义与组件访问器。 */
@Value
public class OrderQuery {

    String buyerId;
    OrderStatus status;
    int page;
    int size;

    public OrderQuery(String buyerId, OrderStatus status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
        this.buyerId = buyerId;
        this.status = status;
        this.page = page;
        this.size = size;
    }

    public String buyerId() {
        return buyerId;
    }

    public OrderStatus status() {
        return status;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }
}
