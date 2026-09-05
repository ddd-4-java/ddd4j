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

import io.ddd4j.kit.text.StrPool;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lambda 查询条件记录（ORM 无关）。
 *
 * <p>存储从 {@link io.ddd4j.core.util.SFunction} 方法引用中解析出的属性名、操作符和值，
 * 由各 ORM 模块的 Repository 转换为原生查询条件。
 *
 * @param propertyRef 类型安全属性引用
 * @param operator    操作符（如 {@code "="}、{@code "LIKE"}、{@code ">"}）
 * @param value       条件值
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class LambdaCondition implements Serializable {

    private final PropertyRef propertyRef;
    private final String operator;
    private final Object value;

    public LambdaCondition(PropertyRef propertyRef, String operator, Object value) {
        Objects.requireNonNull(propertyRef, "propertyRef must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        this.propertyRef = propertyRef;
        this.operator = operator;
        this.value = value;
    }

    public PropertyRef propertyRef() {
        return propertyRef;
    }

    public String operator() {
        return operator;
    }

    public Object value() {
        return value;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LambdaCondition)) return false;
        LambdaCondition that = (LambdaCondition) o;
        return Objects.equals(propertyRef, that.propertyRef)
                && Objects.equals(operator, that.operator)
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(propertyRef);
        result = 31 * result + Objects.hashCode(operator);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return "LambdaCondition{propertyRef=" + propertyRef + ", operator=" + operator
                + ", value=" + value + '}';
    }

    public PropertyRef getPropertyRef() {
        return propertyRef;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }
}
