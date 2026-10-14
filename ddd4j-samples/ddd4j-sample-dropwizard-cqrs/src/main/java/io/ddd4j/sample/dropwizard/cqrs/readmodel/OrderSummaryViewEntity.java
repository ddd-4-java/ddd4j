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
package io.ddd4j.sample.dropwizard.cqrs.readmodel;

import java.time.LocalDateTime;

/**
 * 订单摘要读模型实体（CQRS 读侧）。
 *
 * <p>由投影视图 {@link OrderSummaryView} 通过事件驱动维护。
 */
public class OrderSummaryViewEntity {

    private String orderId;
    private String orderNo;
    private String buyerId;
    private String buyerName;
    private String status;
    private LocalDateTime createdAt;

    /**
     * 创建订单视图实体。
     */
    public OrderSummaryViewEntity() {
    }

    /**
     * 创建订单视图实体。
     *
     * @param orderId 订单标识
     * @param orderNo 订单编号
     * @param buyerId 买家标识
     * @param buyerName 买家名称
     * @param status 订单状态
     */
    public OrderSummaryViewEntity(String orderId, String orderNo, String buyerId, String buyerName, String status) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 读取订单标识。
     *
     * @return 订单标识
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * 设置订单标识。
     *
     * @param orderId 订单标识
     */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /**
     * 读取订单编号。
     *
     * @return 订单编号
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置订单编号。
     *
     * @param orderNo 订单编号
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * 读取买家标识。
     *
     * @return 买家标识
     */
    public String getBuyerId() {
        return buyerId;
    }

    /**
     * 设置买家标识。
     *
     * @param buyerId 买家标识
     */
    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    /**
     * 读取买家名称。
     *
     * @return 买家名称
     */
    public String getBuyerName() {
        return buyerName;
    }

    /**
     * 设置买家名称。
     *
     * @param buyerName 买家名称
     */
    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    /**
     * 读取订单状态。
     *
     * @return 订单状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置订单状态。
     *
     * @param status 订单状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 读取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
