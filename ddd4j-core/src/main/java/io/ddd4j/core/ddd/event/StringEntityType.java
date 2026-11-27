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
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;

/**
 * 基于字符串的实体类型值对象。
 */
public final class StringEntityType implements EntityType {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;

    /**
     * 字符串实体类型值。
     */
    private final String value;

    /**
     * 创建实体类型。
     *
     * @param value 非空白类型文本
     */
    public StringEntityType(String value) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException("Entity type must not be blank");
        }
        this.value = value;
    }

    /**
     * 返回聚合或实体标识的业务字符串。
     *
     * @return 不含类型标记的业务标识
     */
    @Override
    @JsonValue
    public String asString() {
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
        if (!(object instanceof StringEntityType)) {
            return false;
        }
        StringEntityType that = (StringEntityType) object;
        return Objects.equals(value, that.value);
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
        return value;
    }

}
