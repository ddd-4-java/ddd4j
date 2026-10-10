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
package io.ddd4j.core.cqrs.query;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

import io.ddd4j.kit.text.StrPool;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lambda 查询条件记录（ORM 无关）。
 *
 * <p>存储从 {@link io.ddd4j.core.util.SFunction} 方法引用中解析出的属性名、操作符和值，
 * 由各 ORM 模块的 Repository 转换为原生查询条件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */

public final class LambdaCondition implements Serializable {

    private static final long serialVersionUID = 0L;

    private final PropertyRef propertyRef;

    private final String operator;

    private final Object value;

    /**
 * @param propertyRef 类型安全属性引用
 * @param operator 操作符（如 {@code "="}、{@code "LIKE"}、{@code ">"}）
 * @param value 条件值
 */

    @JsonCreator()
    public LambdaCondition(@JsonProperty("propertyRef") PropertyRef propertyRef, @JsonProperty("operator") String operator, @JsonProperty("value") Object value) {
        Objects.requireNonNull(propertyRef, "propertyRef must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        this.propertyRef = propertyRef;
        this.operator = operator;
        this.value = value;
    }

    public String property() {
        return propertyRef.property();
    }

    /**
     * 排序条件构造器。
     */
    public static LambdaCondition asc(PropertyRef property) {
        return new LambdaCondition(property, StrPool.ASC, null);
    }

    public static LambdaCondition desc(PropertyRef property) {
        return new LambdaCondition(property, StrPool.DESC, null);
    }

    /**
     * 是否为排序条件。
     */
    public boolean isOrderBy() {
        return StrPool.ASC.equals(operator) || StrPool.DESC.equals(operator);
    }

    @JsonProperty("propertyRef")
    public PropertyRef propertyRef() {
        return propertyRef;
    }

    @JsonProperty("operator")
    public String operator() {
        return operator;
    }

    @JsonProperty("value")
    public Object value() {
        return value;
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            return new LambdaCondition(propertyRef, operator, value);
        } catch (RuntimeException cause) {
            InvalidObjectException failure = new InvalidObjectException(cause.getMessage());
            failure.initCause(cause);
            throw failure;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        LambdaCondition other = (LambdaCondition) obj;
        return Objects.equals(this.propertyRef, other.propertyRef) && Objects.equals(this.operator, other.operator) && Objects.equals(this.value, other.value);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(propertyRef);
        result = 31 * result + Objects.hashCode(operator);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return "LambdaCondition[propertyRef=" + propertyRef + ", operator=" + operator + ", value=" + value + "]";
    }
}
