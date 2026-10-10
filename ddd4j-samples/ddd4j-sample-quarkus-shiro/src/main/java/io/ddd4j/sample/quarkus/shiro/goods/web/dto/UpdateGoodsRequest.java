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
package io.ddd4j.sample.quarkus.shiro.goods.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.math.BigDecimal;

/**
 * 更新商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class UpdateGoodsRequest {

    private static final long serialVersionUID = 0L;

    private final String name;

    private final BigDecimal price;

    @JsonCreator()
    public UpdateGoodsRequest(@JsonProperty("name") String name, @JsonProperty("price") BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        UpdateGoodsRequest other = (UpdateGoodsRequest) obj;
        return Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(price);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateGoodsRequest[name=" + name + ", price=" + price + "]";
    }
}
