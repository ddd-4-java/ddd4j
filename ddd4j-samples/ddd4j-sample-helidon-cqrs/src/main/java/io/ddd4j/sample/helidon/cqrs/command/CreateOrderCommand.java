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
package io.ddd4j.sample.helidon.cqrs.command;

import java.util.Objects;
import io.ddd4j.core.cqrs.command.Command;

/**
 * 创建订单命令（CQRS 写侧）。
 *
 * @param orderNo   订单编号
 * @param buyerId   买家 ID
 * @param buyerName 买家名称
 */public final class CreateOrderCommand {
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;

        public CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateOrderCommand other = (CreateOrderCommand) o;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderNo, buyerId, buyerName); }
        @Override
        public String toString() {
            return "CreateOrderCommand{" + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + "}";
        }
    }
