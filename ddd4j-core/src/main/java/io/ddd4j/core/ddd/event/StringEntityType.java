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

import java.io.Serial;
import java.util.Objects;

/**
 * 基于字符串的实体类型值对象。
 */
public final class StringEntityType implements EntityType {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * asString() 方法。
     * @return 结果字符串。
     */
    @Override
    @JsonValue
    public String asString() {
        return value;
    }

    /**
     * 判断与另一对象是否相等。
     * @param object 待分配主键的实体对象。
     * @return 相等返回 {@code true}，否则返回 {@code false}。
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StringEntityType that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    /**
     * 返回对象哈希码。
     * @return 哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * 返回对象的字符串表示。
     * @return 对象的字符串表示。
     */
    @Override
    public String toString() {
        return value;
    }

}
