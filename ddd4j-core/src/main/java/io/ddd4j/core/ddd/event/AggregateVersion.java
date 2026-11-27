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
package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 聚合根的单调版本号。
 */
public final class AggregateVersion implements Serializable {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 聚合版本号。
     */
    private final long value;

    /**
     * 创建版本号。
     *
     * @param value 大于等于零的版本号
     */
    public AggregateVersion(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Aggregate version must not be negative");
        }
        this.value = value;
    }

    /**
     * 返回版本号。
     *
     * @return 版本号
     */
    @JsonValue
    public long asInt() {
        return value;
    }

    /**
     * 根据<code>value</code>比较对象的值是否相等。
     *
     * @param object 用于此操作的 <code>object</code>（<code>Object</code>）
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AggregateVersion that)) {
            return false;
        }
        return value == that.value;
    }

    /**
     * 用<code>value</code>计算哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * 生成用于调试和日志的对象描述。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
