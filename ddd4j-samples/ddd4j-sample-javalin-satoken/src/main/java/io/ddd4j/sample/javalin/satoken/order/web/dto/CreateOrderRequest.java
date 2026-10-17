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

    /**
     * 创建空请求，供反序列化使用。
     */
    public CreateOrderRequest() {
    }

    /**
     * 创建请求。
     * @param orderNo 订单编号
     * @param buyerId 买家 ID
     * @param buyerName 买家名称
     */
    @JsonCreator()
    public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
    }

    /**
     * 读取订单编号。
     * @return 订单编号
     */
    @JsonProperty("orderNo")
    public String orderNo() {
        return orderNo;
    }

    /**
     * 读取买家 ID。
     * @return 买家 ID
     */
    @JsonProperty("buyerId")
    public String buyerId() {
        return buyerId;
    }

    /**
     * 读取买家名称。
     * @return 买家名称
     */
    @JsonProperty("buyerName")
    public String buyerName() {
        return buyerName;
    }

    /**
     * 读取订单编号。
     * @return 订单编号
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置订单编号。
     * @param orderNo 订单编号
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * 读取买家 ID。
     * @return 买家 ID
     */
    public String getBuyerId() {
        return buyerId;
    }

    /**
     * 设置买家 ID。
     * @param buyerId 买家 ID
     */
    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    /**
     * 读取买家名称。
     * @return 买家名称
     */
    public String getBuyerName() {
        return buyerName;
    }

    /**
     * 设置买家名称。
     * @param buyerName 买家名称
     */
    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    /**
     * 判断对象是否可参与值比较。
     * @param other 待比较对象
     * @return 是否为同类请求
     */
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
