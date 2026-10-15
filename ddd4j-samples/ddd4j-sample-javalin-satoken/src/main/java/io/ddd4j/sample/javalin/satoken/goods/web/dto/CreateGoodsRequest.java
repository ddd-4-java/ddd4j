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
package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.math.BigDecimal;

/**
 * 创建商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CreateGoodsRequest {

    private static final long serialVersionUID = 0L;

    private String code;

    private String name;

    private BigDecimal price;

    private Integer stock;

    /**
     * 创建空请求，供反序列化使用。
     */
    public CreateGoodsRequest() {
    }

    /**
     * 创建请求。
     * @param code 商品编码
     * @param name 商品名称
     * @param price 价格
     * @param stock 库存
     */
    @JsonCreator()
    public CreateGoodsRequest(@JsonProperty("code") String code, @JsonProperty("name") String name, @JsonProperty("price") BigDecimal price, @JsonProperty("stock") Integer stock) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /**
     * 读取商品编码。
     * @return 商品编码
     */
    @JsonProperty("code")
    public String code() {
        return code;
    }

    /**
     * 读取商品名称。
     * @return 商品名称
     */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /**
     * 读取价格。
     * @return 价格
     */
    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    /**
     * 读取库存。
     * @return 库存
     */
    @JsonProperty("stock")
    public Integer stock() {
        return stock;
    }

    /**
     * 读取商品编码。
     * @return 商品编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置商品编码。
     * @param code 商品编码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 读取商品名称。
     * @return 商品名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置商品名称。
     * @param name 商品名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 读取价格。
     * @return 价格
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 设置价格。
     * @param price 价格
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 读取库存。
     * @return 库存
     */
    public Integer getStock() {
        return stock;
    }

    /**
     * 设置库存。
     * @param stock 库存
     */
    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * 判断对象是否可参与值比较。
     * @param other 待比较对象
     * @return 是否为同类请求
     */
    protected boolean canEqual(Object other) {
        return other instanceof CreateGoodsRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CreateGoodsRequest)) {
            return false;
        }
        CreateGoodsRequest other = (CreateGoodsRequest) o;
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(this.getCode(), other.getCode()) && Objects.equals(this.getName(), other.getName()) && Objects.equals(this.getPrice(), other.getPrice()) && Objects.equals(this.getStock(), other.getStock());
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 59 * result + (Objects.isNull(getStock()) ? 43 : getStock().hashCode());
        result = 59 * result + (Objects.isNull(getCode()) ? 43 : getCode().hashCode());
        result = 59 * result + (Objects.isNull(getName()) ? 43 : getName().hashCode());
        result = 59 * result + (Objects.isNull(getPrice()) ? 43 : getPrice().hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "CreateGoodsRequest[code=" + code + ", name=" + name + ", price=" + price + ", stock=" + stock + "]";
    }
}
