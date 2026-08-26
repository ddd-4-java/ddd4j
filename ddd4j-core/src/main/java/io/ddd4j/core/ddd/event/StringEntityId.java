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
 * 通用字符串实体标识。
 *
 * <p>用于轻量业务和兼容迁移场景。具有独立领域语义的聚合应定义专用
 * {@link EntityId} 类型，避免不同聚合的字符串标识被误用。</p>
 */
public final class StringEntityId implements EntityId {

    @Serial
    private static final long serialVersionUID = 1L;

    static final EntityType TYPE = new StringEntityType("String");

    private final String value;

    /**
     * 创建字符串实体标识。
     *
     * @param value 非空白标识值
     */
    public StringEntityId(String value) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException("Entity id must not be blank");
        }
        this.value = value;
    }

    @Override
    public EntityType getType() {
        return TYPE;
    }

    @Override
    @JsonValue
    public String asString() {
        return value;
    }

    @Override
    public String asTypedString() {
        return TYPE.asString() + ":" + value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StringEntityId)) {
            return false;
        }
        StringEntityId that = (StringEntityId) object;
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
