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
package io.ddd4j.sample.javalin.satoken.order.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 创建订单 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CreateOrderRequest {

    private static final long serialVersionUID = 0L;

    private String orderNo;

    private String buyerId;

    private String buyerName;

    public CreateOrderRequest() {
    }

    @JsonCreator()
    public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
    }

    @JsonProperty("orderNo")
    public String orderNo() {
        return orderNo;
    }

    @JsonProperty("buyerId")
    public String buyerId() {
        return buyerId;
    }

    @JsonProperty("buyerName")
    public String buyerName() {
        return buyerName;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    protected boolean canEqual(Object other) {
        return other instanceof CreateOrderRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (Objects.isNull(o) || !(o instanceof CreateOrderRequest)) {
            return false;
        }
        CreateOrderRequest other = (CreateOrderRequest) o;
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(this.getOrderNo(), other.getOrderNo()) && Objects.equals(this.getBuyerId(), other.getBuyerId()) && Objects.equals(this.getBuyerName(), other.getBuyerName());
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 59 * result + (Objects.isNull(getOrderNo()) ? 43 : getOrderNo().hashCode());
        result = 59 * result + (Objects.isNull(getBuyerId()) ? 43 : getBuyerId().hashCode());
        result = 59 * result + (Objects.isNull(getBuyerName()) ? 43 : getBuyerName().hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "CreateOrderRequest[orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + "]";
    }
}
