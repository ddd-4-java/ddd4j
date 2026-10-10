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
import java.io.Serializable;
import java.util.Objects;

/**
 * 领域事件类型的稳定标识。
 */
public final class EventType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;

    /**
     * 创建事件类型。
     *
     * @param value 非空白且不超过 255 个字符的类型名
     */
    public EventType(String value) {
        if (StrKit.isBlank(value) || value.length() > 255) {
            throw new IllegalArgumentException("Event type must contain 1 to 255 characters");
        }
        this.value = value;
    }

    /**
     * 返回事件类型文本。
     *
     * @return 事件类型文本
     */
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
        if (!(object instanceof EventType that)) {
            return false;
        /**
         * Objects.equals() 方法。
         * @param value 值。
         * @param that.value that.value 参数。
         * @return 方法执行结果。
         */
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
