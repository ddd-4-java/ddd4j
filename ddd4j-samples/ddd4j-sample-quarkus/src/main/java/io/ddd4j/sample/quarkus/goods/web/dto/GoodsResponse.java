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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.sample.quarkus.goods.domain.Goods;
import io.ddd4j.sample.quarkus.goods.domain.GoodsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Stable HTTP representation that keeps the domain model independent of JSON concerns.
 */
public final class GoodsResponse {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u793a\u4f8b\u5546\u54c1\u7684\u4e3b\u952e\u3002 */
    private final Long id;

    /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u4e1a\u52a1\u7f16\u7801\u3002 */
    private final String code;

    /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u663e\u793a\u540d\u79f0\u3002 */
    private final String name;

    /** \u5546\u54c1\u793a\u4f8b\u4f7f\u7528\u7684\u9500\u552e\u4ef7\u683c\u3002 */
    private final BigDecimal price;

    /** \u793a\u4f8b\u5546\u54c1\u53ef\u7528\u7684\u5e93\u5b58\u6570\u91cf\u3002 */
    private final Integer stock;

    /** \u5f53\u524d\u5546\u54c1\u3001\u8d26\u53f7\u6216\u6743\u9650\u8bb0\u5f55\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
    private final GoodsStatus status;

    /** \u5546\u54c1\u521b\u5efa\u65f6\u95f4\u3002 */
    private final LocalDateTime createTime;

    /** \u6700\u8fd1\u4e00\u6b21\u66f4\u65b0\u793a\u4f8b\u5546\u54c1\u7684\u65f6\u95f4\u3002 */
    private final LocalDateTime updateTime;

    /**
     * @param id         商品标识
     * @param code       商品编码
     * @param name       商品名称
     * @param price      商品价格
     * @param stock      库存数量
     * @param status     商品状态
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
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

    /**
     * @param goods 商品领域对象
     * @return 商品 HTTP 响应
     */
    public static GoodsResponse from(Goods goods) {
        Goods source = Objects.requireNonNull(goods, "goods must not be null");
        return new GoodsResponse(source.id(), source.getCode(), source.getName(), source.getPrice(), source.getStock(), source.getStatus(), source.getCreateTime(), source.getUpdateTime());
    }

    /**
     * @return 商品标识
     */
    @JsonProperty("id")
    public Long id() {
        return id;
    }

    /**
     * @return 商品编码
     */
    @JsonProperty("code")
    public String code() {
        return code;
    }

    /**
     * @return 商品名称
     */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /**
     * @return 商品价格
     */
    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    /**
     * @return 库存数量
     */
    @JsonProperty("stock")
    public Integer stock() {
        return stock;
    }

    /**
     * @return 商品状态
     */
    @JsonProperty("status")
    public GoodsStatus status() {
        return status;
    }

    /**
     * @return 创建时间
     */
    @JsonProperty("createTime")
    public LocalDateTime createTime() {
        return createTime;
    }

    /**
     * @return 更新时间
     */
    @JsonProperty("updateTime")
    public LocalDateTime updateTime() {
        return updateTime;
    }

    /**
     * 根据<code>id</code>、<code>code</code>、<code>name</code>、<code>price</code>、<code>stock</code>、<code>status</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
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

    /**
     * 生成用于调试和日志的对象描述，包含<code>id</code>、<code>code</code>、<code>name</code>、<code>price</code>、<code>stock</code>、<code>status</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "GoodsResponse[id=" + id + ", code=" + code + ", name=" + name + ", price=" + price + ", stock=" + stock + ", status=" + status + ", createTime=" + createTime + ", updateTime=" + updateTime + "]";
    }
}
