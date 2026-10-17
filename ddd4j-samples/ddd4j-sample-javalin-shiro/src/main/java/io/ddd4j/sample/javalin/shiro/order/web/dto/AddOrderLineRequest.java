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
package io.ddd4j.sample.javalin.shiro.order.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.math.BigDecimal;

/**
 * 添加订单行 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AddOrderLineRequest {

    private static final long serialVersionUID = 0L;

    private String goodsId;

    private String goodsName;

    private int quantity;

    private BigDecimal unitPrice;

    /**
     * 创建空请求，供反序列化使用。
     */
    public AddOrderLineRequest() {
    }

    /**
     * 创建请求。
     * @param goodsId 商品 ID
     * @param goodsName 商品名称
     * @param quantity 数量
     * @param unitPrice 单价
     */
    @JsonCreator()
    public AddOrderLineRequest(@JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /**
     * 读取商品 ID。
     * @return 商品 ID
     */
    @JsonProperty("goodsId")
    public String goodsId() {
        return goodsId;
    }

    /**
     * 读取商品名称。
     * @return 商品名称
     */
    @JsonProperty("goodsName")
    public String goodsName() {
        return goodsName;
    }

    /**
     * 读取数量。
     * @return 数量
     */
    @JsonProperty("quantity")
    public int quantity() {
        return quantity;
    }

    /**
     * 读取单价。
     * @return 单价
     */
    @JsonProperty("unitPrice")
    public BigDecimal unitPrice() {
        return unitPrice;
    }

    /**
     * 读取商品 ID。
     * @return 商品 ID
     */
    public String getGoodsId() {
        return goodsId;
    }

    /**
     * 设置商品 ID。
     * @param goodsId 商品 ID
     */
    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    /**
     * 读取商品名称。
     * @return 商品名称
     */
    public String getGoodsName() {
        return goodsName;
    }

    /**
     * 设置商品名称。
     * @param goodsName 商品名称
     */
    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    /**
     * 读取数量。
     * @return 数量
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * 设置数量。
     * @param quantity 数量
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * 读取单价。
     * @return 单价
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置单价。
     * @param unitPrice 单价
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 判断对象是否可参与值比较。
     * @param other 待比较对象
     * @return 是否为同类请求
     */
    protected boolean canEqual(Object other) {
        return other instanceof AddOrderLineRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AddOrderLineRequest)) {
            return false;
        }
        AddOrderLineRequest other = (AddOrderLineRequest) o;
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(this.getGoodsId(), other.getGoodsId()) && Objects.equals(this.getGoodsName(), other.getGoodsName()) && this.getQuantity() == other.getQuantity() && Objects.equals(this.getUnitPrice(), other.getUnitPrice());
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 59 * result + getQuantity();
        result = 59 * result + (Objects.isNull(getGoodsId()) ? 43 : getGoodsId().hashCode());
        result = 59 * result + (Objects.isNull(getGoodsName()) ? 43 : getGoodsName().hashCode());
        result = 59 * result + (Objects.isNull(getUnitPrice()) ? 43 : getUnitPrice().hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "AddOrderLineRequest[goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "]";
    }
}
