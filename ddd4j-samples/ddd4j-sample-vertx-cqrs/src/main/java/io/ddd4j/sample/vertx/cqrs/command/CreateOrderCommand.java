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
package io.ddd4j.sample.vertx.cqrs.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.command.Command;

import java.util.Objects;

/**
 * 创建订单命令（CQRS 写侧）。
 *
 * <p>{@code orderNo} — 订单编号
 * <p>{@code buyerId} — 买家 ID
 * <p>{@code buyerName} — 买家名称
 */
public final class CreateOrderCommand implements Command {
    private static final long serialVersionUID = 0L;
    private final String orderNo;
    private final String buyerId;
    private final String buyerName;

    /**
     * 创建可由 JSON 还原的订单命令。
     *
     * @param orderNo 订单编号
     * @param buyerId 买家 ID
     * @param buyerName 买家名称
     */
    @JsonCreator
    public CreateOrderCommand(@JsonProperty("orderNo") String orderNo,
                              @JsonProperty("buyerId") String buyerId,
                              @JsonProperty("buyerName") String buyerName) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
    }

    /** @return 订单编号 */
    @JsonProperty("orderNo")
    public String orderNo() {
        return orderNo;
    }

    /** @return 买家标识 */
    @JsonProperty("buyerId")
    public String buyerId() {
        return buyerId;
    }

    /** @return 买家名称 */
    @JsonProperty("buyerName")
    public String buyerName() {
        return buyerName;
    }

    /** @return 订单编号 */
    public String getOrderNo() {
        return orderNo;
    }

    /** @return 买家标识 */
    public String getBuyerId() {
        return buyerId;
    }

    /** @return 买家名称 */
    public String getBuyerName() {
        return buyerName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        CreateOrderCommand other = (CreateOrderCommand) obj;
        return Objects.equals(this.orderNo, other.orderNo)
                && Objects.equals(this.buyerId, other.buyerId)
                && Objects.equals(this.buyerName, other.buyerName);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 59 * result + (Objects.isNull(orderNo) ? 43 : orderNo.hashCode());
        result = 59 * result + (Objects.isNull(buyerId) ? 43 : buyerId.hashCode());
        result = 59 * result + (Objects.isNull(buyerName) ? 43 : buyerName.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "CreateOrderCommand(orderNo=" + orderNo + ", buyerId=" + buyerId
                + ", buyerName=" + buyerName + ")";
    }
}
