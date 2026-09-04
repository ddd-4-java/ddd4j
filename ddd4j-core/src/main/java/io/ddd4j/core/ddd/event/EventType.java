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

import java.io.Serializable;
import java.util.Objects;

/**
 * 领域事件类型的稳定标识。
 */
public final class EventType implements Serializable {
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EventType)) {
            return false;
        }
        EventType that = (EventType) object;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }

}
