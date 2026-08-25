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

    @Serial
    private static final long serialVersionUID = 1L;

    private final int value;

    /**
     * 创建版本号。
     *
     * @param value 大于等于零的版本号
     */
    public AggregateVersion(int value) {
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
    public int asInt() {
        return value;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
