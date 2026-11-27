package io.ddd4j.core.cqrs.query;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

import io.ddd4j.core.util.LambdaKit;
import io.ddd4j.core.util.SFunction;
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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u5c5e\u6027\u6240\u5c5e\u7a7a\u95f4\u3002 */
    private final PropertySpace space;

    /** \u58f0\u660e\u5c5e\u6027\u65b9\u6cd5\u7684\u7c7b\u578b\u3002 */
    private final Class<?> ownerType;

    /** Java \u5c5e\u6027\u540d\u3002 */
    private final String property;

    /**
     * 组合属性空间、属性名称和 Java 类型。
     *
     * @param space \u5c5e\u6027\u7a7a\u95f4
     * @param ownerType \u58f0\u660e\u5c5e\u6027\u65b9\u6cd5\u7684\u7c7b\u578b
     * @param property Java \u5c5e\u6027\u540d
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
     * \u4ece\u9886\u57df\u6a21\u578b\u5c5e\u6027\u65b9\u6cd5\u5f15\u7528\u521b\u5efa\u5c5e\u6027\u6807\u8bc6\u3002
     * @param <M> \u9886\u57df\u6a21\u578b\u7c7b\u578b
     * @param function \u9886\u57df\u6a21\u578b\u5c5e\u6027\u65b9\u6cd5\u5f15\u7528
     * @return \u9886\u57df\u5c5e\u6027\u6807\u8bc6
     */
    public static <M> PropertyRef domain(SFunction<M, ?> function) {
        Objects.requireNonNull(function, "function must not be null");
        return new PropertyRef(PropertySpace.DOMAIN, LambdaKit.resolveType(function), LambdaKit.resolve(function));
    }

    /**
     * \u4ece\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u65b9\u6cd5\u5f15\u7528\u521b\u5efa\u5c5e\u6027\u6807\u8bc6\uff0c\u5e76\u9a8c\u8bc1\u58f0\u660e\u7c7b\u578b\u3002
     * @param <P> \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     * @param persistenceType \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     * @param function \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u65b9\u6cd5\u5f15\u7528
     * @return \u6301\u4e45\u5316\u5c5e\u6027\u6807\u8bc6
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
     * \u9a8c\u8bc1\u5c5e\u6027\u5f15\u7528\u4e0e\u5f53\u524d Repository \u7684 Domain/PO \u7c7b\u578b\u4e00\u81f4\u3002
     *
     * @param domainType \u4ed3\u50a8\u7684\u9886\u57df\u6a21\u578b\u7c7b\u578b
     * @param persistenceType \u4ed3\u50a8\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
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
     * \u5224\u65ad\u5c5e\u6027\u662f\u5426\u5c5e\u4e8e\u9886\u57df\u6a21\u578b\u7a7a\u95f4\u3002
     * @return \u5c5e\u4e8e\u9886\u57df\u7a7a\u95f4\u65f6\u4e3a true
     */
    public boolean isDomain() {
        return Objects.equals(PropertySpace.DOMAIN, space);
    }

    /**
     * \u5224\u65ad\u5c5e\u6027\u662f\u5426\u5c5e\u4e8e\u6301\u4e45\u5316\u5bf9\u8c61\u7a7a\u95f4\u3002
     * @return \u5c5e\u4e8e\u6301\u4e45\u5316\u7a7a\u95f4\u65f6\u4e3a true
     */
    public boolean isPersistence() {
        return Objects.equals(PropertySpace.PERSISTENCE, space);
    }

    /**
     * \u6784\u9020\u5c5e\u6027\u58f0\u660e\u7c7b\u578b\u4e0e\u5f53\u524d\u67e5\u8be2\u4f5c\u7528\u57df\u4e0d\u517c\u5bb9\u7684\u9519\u8bef\u3002
     * @param space \u9886\u57df\u6216\u6301\u4e45\u5316\u5c5e\u6027\u7a7a\u95f4
     * @param ownerType \u5c5e\u6027\u58f0\u660e\u6240\u5728\u7684 Java \u7c7b\u578b
     * @param expectedType \u5f53\u524d\u4f5c\u7528\u57df\u63a5\u53d7\u7684\u7c7b\u578b
     * @return \u63cf\u8ff0\u5c5e\u6027\u7c7b\u578b\u4e0d\u517c\u5bb9\u7684\u53c2\u6570\u5f02\u5e38
     */
    private static IllegalArgumentException incompatible(PropertySpace space, Class<?> ownerType, Class<?> expectedType) {
        return new IllegalArgumentException("Query " + space + " property owner " + ownerType.getName() + " is incompatible with repository type " + expectedType.getName());
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u5c5e\u6027\u7a7a\u95f4\u3002
     * @return \u5c5e\u6027\u7a7a\u95f4
     */
    @JsonProperty("space")
    public PropertySpace space() {
        return space;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u5c5e\u6027\u58f0\u660e\u7c7b\u578b\u3002
     * @return \u5c5e\u6027\u58f0\u660e\u7c7b\u578b
     */
    @JsonProperty("ownerType")
    public Class<?> ownerType() {
        return ownerType;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u5c5e\u6027\u540d\u3002
     * @return Java \u5c5e\u6027\u540d
     */
    @JsonProperty("property")
    public String property() {
        return property;
    }

    /**
     * \u53cd\u5e8f\u5217\u5316\u65f6\u91cd\u65b0\u6821\u9a8c\u5c5e\u6027\u7a7a\u95f4\u3001\u58f0\u660e\u7c7b\u578b\u548c\u5c5e\u6027\u540d\u3002
     * @return \u6821\u9a8c\u540e\u7684\u5c5e\u6027\u5f15\u7528
     * @throws ObjectStreamException \u5c5e\u6027\u5f15\u7528\u65e0\u6548\u65f6
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

    /**
     * 根据<code>space</code>、<code>ownerType</code>、<code>property</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(space);
        result = 31 * result + Objects.hashCode(ownerType);
        result = 31 * result + Objects.hashCode(property);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>space</code>、<code>ownerType</code>、<code>property</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "PropertyRef[space=" + space + ", ownerType=" + ownerType + ", property=" + property + "]";
    }
}
