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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/** 按买家和状态筛选订单的分页查询条件。 */
public final class OrderQuery {

    private static final long serialVersionUID = 0L;

    /** 可选的买家标识。 */
    private final String buyerId;

    /** 可选的订单状态。 */
    private final OrderStatus status;

    /** 从一开始的页码。 */
    private final int page;

    /** 每页数量。 */
    private final int size;

    /**
     * 创建分页查询条件。
     *
     * @param buyerId 可选买家标识
     * @param status 可选订单状态
     * @param page 从一开始的页码
     * @param size 每页数量，范围一至一百
     */
    @JsonCreator()
    public OrderQuery(@JsonProperty("buyerId") String buyerId, @JsonProperty("status") OrderStatus status, @JsonProperty("page") int page, @JsonProperty("size") int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
        this.buyerId = buyerId;
        this.status = status;
        this.page = page;
        this.size = size;
    }

    /** @return 买家标识 */
    @JsonProperty("buyerId")
    public String buyerId() {
        return buyerId;
    }

    /** @return 订单状态 */
    @JsonProperty("status")
    public OrderStatus status() {
        return status;
    }

    /** @return 从一开始的页码 */
    @JsonProperty("page")
    public int page() {
        return page;
    }

    /** @return 每页数量 */
    @JsonProperty("size")
    public int size() {
        return size;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        OrderQuery other = (OrderQuery) obj;
        return Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.status, other.status) && this.page == other.page && this.size == other.size;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(buyerId);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Integer.hashCode(page);
        result = 31 * result + Integer.hashCode(size);
        return result;
    }

    @Override
    public String toString() {
        return "OrderQuery[buyerId=" + buyerId + ", status=" + status + ", page=" + page + ", size=" + size + "]";
    }
}
