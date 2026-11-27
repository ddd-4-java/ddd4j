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
package io.ddd4j.sample.quarkus.shiro.goods.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * 商品标识值对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GoodsId implements Serializable {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;

    /**
     * 标识数值。
     */
    private final Long value;

    /**
     * \u7528\u6301\u4e45\u5316\u4e3b\u952e\u521b\u5efa\u5546\u54c1\u6807\u8bc6\u3002
     * @param value \u6784\u9020\u6807\u8bc6\u6216\u914d\u7f6e\u5bf9\u8c61\u7684\u539f\u503c
     */
    private GoodsId(Long value) {
        if (Objects.isNull(value) || value <= 0) {
            throw new IllegalArgumentException("goodsId must be positive");
        }
        this.value = value;
    }

    /**
     * 从数值创建商品标识。
     *
     * @param value 标识数值
     * @return 商品标识
     */
    public static GoodsId of(Long value) {
        return new GoodsId(value);
    }

    /**
     * 读取商品标识数值。
     *
     * @return 标识数值
     */
    public Long value() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoodsId)) {
            return false;
        }
        GoodsId that = (GoodsId) o;
        return Objects.equals(value, that.value);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GoodsId{" + value + '}';
    }
}
