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
package io.ddd4j.sample.quarkus.goods.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.math.BigDecimal;

/**
 * 创建商品请求 DTO。
 *
 * <p>轻量不可变对象：与 Spring MVC 的 record 参数绑定风格一致，
 * 同时被 JAX-RS / Quarkus REST 原生支持。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class CreateGoodsRequest {

    private static final long serialVersionUID = 0L;

    private final String code;

    private final String name;

    private final BigDecimal price;

    private final Integer stock;

    /**
     * @param code 商品编码
     * @param name 商品名称
     * @param price 商品价格
     * @param stock 库存数量
     */
    @JsonCreator()
    public CreateGoodsRequest(@JsonProperty("code") String code, @JsonProperty("name") String name, @JsonProperty("price") BigDecimal price, @JsonProperty("stock") Integer stock) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /** @return 商品编码 */
    @JsonProperty("code")
    public String code() {
        return code;
    }

    /** @return 商品名称 */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /** @return 商品价格 */
    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    /** @return 库存数量 */
    @JsonProperty("stock")
    public Integer stock() {
        return stock;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        CreateGoodsRequest other = (CreateGoodsRequest) obj;
        return Objects.equals(this.code, other.code) && Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price) && Objects.equals(this.stock, other.stock);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(price);
        result = 31 * result + Objects.hashCode(stock);
        return result;
    }

    @Override
    public String toString() {
        return "CreateGoodsRequest[code=" + code + ", name=" + name + ", price=" + price + ", stock=" + stock + "]";
    }
}
