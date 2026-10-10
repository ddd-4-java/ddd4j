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

import io.ddd4j.sample.quarkus.goods.domain.Goods;
import io.ddd4j.sample.quarkus.goods.domain.GoodsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Stable HTTP representation that keeps the domain model independent of JSON concerns.
 */
public final class GoodsResponse {

    private static final long serialVersionUID = 0L;

    private final Long id;

    private final String code;

    private final String name;

    private final BigDecimal price;

    private final Integer stock;

    private final GoodsStatus status;

    private final LocalDateTime createTime;

    private final LocalDateTime updateTime;

    public static GoodsResponse from(Goods goods) {
        Goods source = Objects.requireNonNull(goods, "goods must not be null");
        return new GoodsResponse(source.id(), source.getCode(), source.getName(), source.getPrice(), source.getStock(), source.getStatus(), source.getCreateTime(), source.getUpdateTime());
    }

    @JsonCreator()
    public GoodsResponse(@JsonProperty("id") Long id, @JsonProperty("code") String code, @JsonProperty("name") String name, @JsonProperty("price") BigDecimal price, @JsonProperty("stock") Integer stock, @JsonProperty("status") GoodsStatus status, @JsonProperty("createTime") LocalDateTime createTime, @JsonProperty("updateTime") LocalDateTime updateTime) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    @JsonProperty("id")
    public Long id() {
        return id;
    }

    @JsonProperty("code")
    public String code() {
        return code;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    @JsonProperty("stock")
    public Integer stock() {
        return stock;
    }

    @JsonProperty("status")
    public GoodsStatus status() {
        return status;
    }

    @JsonProperty("createTime")
    public LocalDateTime createTime() {
        return createTime;
    }

    @JsonProperty("updateTime")
    public LocalDateTime updateTime() {
        return updateTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        GoodsResponse other = (GoodsResponse) obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.code, other.code) && Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price) && Objects.equals(this.stock, other.stock) && Objects.equals(this.status, other.status) && Objects.equals(this.createTime, other.createTime) && Objects.equals(this.updateTime, other.updateTime);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(price);
        result = 31 * result + Objects.hashCode(stock);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(createTime);
        result = 31 * result + Objects.hashCode(updateTime);
        return result;
    }

    @Override
    public String toString() {
        return "GoodsResponse[id=" + id + ", code=" + code + ", name=" + name + ", price=" + price + ", stock=" + stock + ", status=" + status + ", createTime=" + createTime + ", updateTime=" + updateTime + "]";
    }
}
