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
package io.ddd4j.sample.javalin.shiro.goods.web.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 更新商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class UpdateGoodsRequest {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u663e\u793a\u540d\u79f0\u3002 */
    private String name;

    /** \u7528\u4e8e\u66ff\u6362\u5546\u54c1\u5f53\u524d\u9500\u552e\u4ef7\u7684\u8bf7\u6c42\u5b57\u6bb5\u3002 */
    private BigDecimal price;

    /**
     * 创建空请求，供反序列化使用。
     */
    public UpdateGoodsRequest() {
    }

    /**
     * 创建请求。
     *
     * @param name  商品名称
     * @param price 价格
     */
    @JsonCreator()
    public UpdateGoodsRequest(@JsonProperty("name") String name, @JsonProperty("price") BigDecimal price) {
        this.name = name;
        this.price = price;
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
     * 读取价格。
     *
     * @return 价格
     */
    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    /**
     * 读取商品名称。
     *
     * @return 商品名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置商品名称。
     *
     * @param name 商品名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 读取价格。
     *
     * @return 价格
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 设置价格。
     *
     * @param price 价格
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 判断对象是否可参与值比较。
     *
     * @param other 待比较对象
     * @return 是否为同类请求
     */
    protected boolean canEqual(Object other) {
        return other instanceof UpdateGoodsRequest;
    }

    /**
     * 比较两个对象的值是否相等。
     *
     * @param o 用来比较的另一个对象，允许为空
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (Objects.isNull(o) || !(o instanceof UpdateGoodsRequest)) {
            return false;
        }
        UpdateGoodsRequest other = (UpdateGoodsRequest) o;
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(this.getName(), other.getName()) && Objects.equals(this.getPrice(), other.getPrice());
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 1;
        result = 59 * result + (Objects.isNull(getName()) ? 43 : getName().hashCode());
        result = 59 * result + (Objects.isNull(getPrice()) ? 43 : getPrice().hashCode());
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>name</code>、<code>price</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "UpdateGoodsRequest[name=" + name + ", price=" + price + "]";
    }
}
