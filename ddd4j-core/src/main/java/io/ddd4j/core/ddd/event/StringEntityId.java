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
 * 通用字符串实体标识。
 *
 * <p>用于轻量业务和兼容迁移场景。具有独立领域语义的聚合应定义专用
 * {@link EntityId} 类型，避免不同聚合的字符串标识被误用。</p>
 */
public final class StringEntityId implements EntityId {
    /** \u5b57\u7b26\u4e32\u5b9e\u4f53\u6807\u8bc6\u7684\u5b9e\u4f53\u7c7b\u578b\u5b9a\u4e49\u3002 */
    static final EntityType TYPE = new StringEntityType("String");
    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;
    /** \u5b57\u7b26\u4e32\u5b9e\u4f53\u6807\u8bc6\u503c\u3002 */
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

    /**
     * 返回当前值或标识公开的类型。
     *
     * @return 实际返回<code>TYPE</code>，供调用方取得当前值
     */
    @Override
    public EntityType getType() {
        return TYPE;
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
     * 返回带类型前缀的标识字符串。
     *
     * <p>此实现调用 <code>TYPE.asString()</code>。</p>
     *
     * @return 包含类型标记的标识
     */
    @Override
    public String asTypedString() {
        return TYPE.asString() + ":" + value;
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
        if (!(object instanceof StringEntityId)) {
            return false;
        }
        StringEntityId that = (StringEntityId) object;
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
