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
import io.ddd4j.kit.lang.SFunction;
import io.ddd4j.kit.lang.LambdaKit;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

import io.ddd4j.kit.lang.StrKit;

import java.io.Serializable;
import java.util.Objects;

/**
 * ORM 无关的类型安全属性引用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */

public final class PropertyRef implements Serializable {

    private static final long serialVersionUID = 0L;

    /**
     * 属性所属空间。
     */
    private final PropertySpace space;

    /**
     * 声明属性方法的类型。
     */
    private final Class<?> ownerType;

    /**
     * Java 属性名。
     */
    private final String property;

    /**
 * @param space 属性空间
 * @param ownerType 声明属性方法的类型
 * @param property Java 属性名
 */

    @JsonCreator()
    public PropertyRef(@JsonProperty("space") PropertySpace space, @JsonProperty("ownerType") Class<?> ownerType, @JsonProperty("property") String property) {
        Objects.requireNonNull(space, "space must not be null");
        Objects.requireNonNull(ownerType, "ownerType must not be null");
        if (StrKit.isEmpty(property)) {
            throw new IllegalArgumentException("property must not be empty");
        }
        this.space = space;
        this.ownerType = ownerType;
        this.property = property;
    }

    /**
     * 从领域模型属性方法引用创建属性标识。
     *
     * @param <M> 领域模型类型
     * @param function 领域模型属性方法引用
     * @return 领域属性标识
     */
    public static <M> PropertyRef domain(SFunction<M, ?> function) {
        Objects.requireNonNull(function, "function must not be null");
        return new PropertyRef(PropertySpace.DOMAIN, LambdaKit.resolveType(function), LambdaKit.resolve(function));
    }

    /**
     * 从持久化对象属性方法引用创建属性标识，并验证声明类型。
     *
     * @param <P> 持久化对象类型
     * @param persistenceType 持久化对象类型
     * @param function 持久化对象属性方法引用
     * @return 持久化属性标识
     */
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
     *
     * @param domainType 仓储的领域模型类型
     * @param persistenceType 仓储的持久化对象类型
     */
    public void requireCompatible(Class<?> domainType, Class<?> persistenceType) {
        Objects.requireNonNull(domainType, "domainType must not be null");
        Objects.requireNonNull(persistenceType, "persistenceType must not be null");
        Class<?> expectedType = Objects.equals(PropertySpace.DOMAIN, space) ? domainType : persistenceType;
        if (!ownerType.isAssignableFrom(expectedType)) {
            throw incompatible(space, ownerType, expectedType);
        }
    }

    /**
     * 判断属性是否属于领域模型空间。
     *
     * @return 属于领域空间时为 true
     */
    public boolean isDomain() {
        return Objects.equals(PropertySpace.DOMAIN, space);
    }

    /**
     * 判断属性是否属于持久化对象空间。
     *
     * @return 属于持久化空间时为 true
     */
    public boolean isPersistence() {
        return Objects.equals(PropertySpace.PERSISTENCE, space);
    }

    private static IllegalArgumentException incompatible(PropertySpace space, Class<?> ownerType, Class<?> expectedType) {
        return new IllegalArgumentException("Query " + space + " property owner " + ownerType.getName() + " is incompatible with repository type " + expectedType.getName());
    }

    /**
     * 获取属性声明类型。
     *
     * @return 属性声明类型
     */
    public Class<?> getOwnerType() {
        return ownerType;
    }

    /**
     * 获取 Java 属性名。
     *
     * @return Java 属性名
     */
    public String getProperty() {
        return property;
    }

    /**
     * 获取属性空间。
     *
     * @return 属性空间
     */
    public PropertySpace getSpace() {
        return space;
    }

    /**
     * 获取用于 JSON 的属性空间。
     *
     * @return 属性空间
     */
    @JsonProperty("space")
    public PropertySpace space() {
        return space;
    }

    /**
     * 获取用于 JSON 的属性声明类型。
     *
     * @return 属性声明类型
     */
    @JsonProperty("ownerType")
    public Class<?> ownerType() {
        return ownerType;
    }

    /**
     * 获取用于 JSON 的属性名。
     *
     * @return Java 属性名
     */
    @JsonProperty("property")
    public String property() {
        return property;
    }

    /**
     * 反序列化时重新校验属性空间、声明类型和属性名。
     *
     * @return 校验后的属性引用
     * @throws ObjectStreamException 属性引用无效时
     */
    private Object readResolve() throws ObjectStreamException {
        try {
            return new PropertyRef(space, ownerType, property);
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
        PropertyRef other = (PropertyRef) obj;
        return Objects.equals(this.space, other.space) && Objects.equals(this.ownerType, other.ownerType) && Objects.equals(this.property, other.property);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(space);
        result = 31 * result + Objects.hashCode(ownerType);
        result = 31 * result + Objects.hashCode(property);
        return result;
    }

    @Override
    public String toString() {
        return "PropertyRef[space=" + space + ", ownerType=" + ownerType + ", property=" + property + "]";
    }
}
