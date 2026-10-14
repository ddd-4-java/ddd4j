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

    public AddOrderLineRequest() {
    }

    @JsonCreator()
    public AddOrderLineRequest(@JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @JsonProperty("goodsId")
    public String goodsId() {
        return goodsId;
    }

    @JsonProperty("goodsName")
    public String goodsName() {
        return goodsName;
    }

    @JsonProperty("quantity")
    public int quantity() {
        return quantity;
    }

    @JsonProperty("unitPrice")
    public BigDecimal unitPrice() {
        return unitPrice;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

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
