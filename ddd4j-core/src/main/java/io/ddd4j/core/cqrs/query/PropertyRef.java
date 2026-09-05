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

import io.ddd4j.core.util.LambdaKit;
import io.ddd4j.core.util.SFunction;
import io.ddd4j.kit.lang.StrKit;

import java.io.Serializable;
import java.util.Objects;

/**
 * ORM 无关的类型安全属性引用。
 *
 * @param space     属性空间
 * @param ownerType 声明属性方法的类型
 * @param property  Java 属性名
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class PropertyRef implements Serializable {
    private final PropertySpace space;
    private final Class<?> ownerType;
    private final String property;

    public PropertyRef(PropertySpace space, Class<?> ownerType, String property) {
        Objects.requireNonNull(space, "space must not be null");
        Objects.requireNonNull(ownerType, "ownerType must not be null");
        if (StrKit.isEmpty(property)) {
            throw new IllegalArgumentException("property must not be empty");
        }
        this.space = space;
        this.ownerType = ownerType;
        this.property = property;
    }

    public static <M> PropertyRef domain(SFunction<M, ?> function) {
        Objects.requireNonNull(function, "function must not be null");
        return new PropertyRef(PropertySpace.DOMAIN, LambdaKit.resolveType(function), LambdaKit.resolve(function));
    }

    public static <P> PropertyRef persistence(Class<P> persistenceType, SFunction<P, ?> function) {
        Objects.requireNonNull(persistenceType, "persistenceType must not be null");
        Objects.requireNonNull(function, "function must not be null");
        Class<?> ownerType = LambdaKit.resolveType(function);
        if (!ownerType.isAssignableFrom(persistenceType)) {
            throw incompatible(PropertySpace.PERSISTENCE, ownerType, persistenceType);
        }
        return new PropertyRef(PropertySpace.PERSISTENCE, ownerType, LambdaKit.resolve(function));
    }

    /**
     * 验证属性引用与当前 Repository 的 Domain/PO 类型一致。
     */
    public void requireCompatible(Class<?> domainType, Class<?> persistenceType) {
        Objects.requireNonNull(domainType, "domainType must not be null");
        Objects.requireNonNull(persistenceType, "persistenceType must not be null");
        Class<?> expectedType = Objects.equals(PropertySpace.DOMAIN, space) ? domainType : persistenceType;
        if (!ownerType.isAssignableFrom(expectedType)) {
            throw incompatible(space, ownerType, expectedType);
        }
    }

    public boolean isDomain() {
        return Objects.equals(PropertySpace.DOMAIN, space);
    }

    public boolean isPersistence() {
        return Objects.equals(PropertySpace.PERSISTENCE, space);
    }

    private static IllegalArgumentException incompatible(PropertySpace space, Class<?> ownerType,
                                                          Class<?> expectedType) {
        return new IllegalArgumentException("Query " + space + " property owner " + ownerType.getName()
                + " is incompatible with repository type " + expectedType.getName());
    }

    public PropertySpace space() {
        return space;
    }

    public Class<?> ownerType() {
        return ownerType;
    }

    public String property() {
        return property;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PropertyRef)) return false;
        PropertyRef that = (PropertyRef) o;
        return Objects.equals(space, that.space) && Objects.equals(ownerType, that.ownerType) && Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(space);
        result = 31 * result + Objects.hashCode(ownerType);
        result = 31 * result + Objects.hashCode(property);
        return result;
    }

    @Override
    public String toString() {
        return "PropertyRef{" + space + ", " + ownerType + ", " + property + '}';
    }

    public PropertySpace getSpace() {
        return space;
    }

    public Class<?> getOwnerType() {
        return ownerType;
    }

    public String getProperty() {
        return property;
    }
}
