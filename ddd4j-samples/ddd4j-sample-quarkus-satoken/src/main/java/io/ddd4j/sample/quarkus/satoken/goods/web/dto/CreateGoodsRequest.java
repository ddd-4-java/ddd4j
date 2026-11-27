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
package io.ddd4j.sample.quarkus.satoken.goods.web.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 创建商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class CreateGoodsRequest {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u4e1a\u52a1\u7f16\u7801\u3002 */
    private final String code;

    /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u663e\u793a\u540d\u79f0\u3002 */
    private final String name;

    /** \u5546\u54c1\u793a\u4f8b\u4f7f\u7528\u7684\u9500\u552e\u4ef7\u683c\u3002 */
    private final BigDecimal price;

    /** \u793a\u4f8b\u5546\u54c1\u53ef\u7528\u7684\u5e93\u5b58\u6570\u91cf\u3002 */
    private final Integer stock;

    /**
     * 创建商品请求。
     *
     * @param code  商品编码
     * @param name  商品名称
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

    /**
     * 读取商品编码。
     *
     * @return 商品编码
     */
    @JsonProperty("code")
    public String code() {
        return code;
    }

    /**
     * 读取商品名称。
     *
     * @return 商品名称
     */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /**
     * 读取商品价格。
     *
     * @return 商品价格
     */
    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    /**
     * 读取库存数量。
     *
     * @return 库存数量
     */
    @JsonProperty("stock")
    public Integer stock() {
        return stock;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(price);
        result = 31 * result + Objects.hashCode(stock);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "CreateGoodsRequest[code=" + code + ", name=" + name + ", price=" + price + ", stock=" + stock + "]";
    }
}
