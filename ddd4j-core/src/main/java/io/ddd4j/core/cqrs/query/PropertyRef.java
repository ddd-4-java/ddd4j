package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.util.LambdaKit;
import io.ddd4j.core.util.SFunction;
import java.io.Serializable;
import java.util.Objects;

/** ORM 无关的类型安全属性引用。 */
public final class PropertyRef implements Serializable {
    private static final long serialVersionUID = 1L;
    private final PropertySpace space; private final Class<?> ownerType; private final String property;
    public PropertyRef(PropertySpace space, Class<?> ownerType, String property) {
        this.space = Objects.requireNonNull(space, "space"); this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        if (property == null || property.trim().isEmpty()) throw new IllegalArgumentException("property must not be empty"); this.property = property;
    }
    public static <M> PropertyRef domain(SFunction<M, ?> function) { return new PropertyRef(PropertySpace.DOMAIN, LambdaKit.resolveType(function), LambdaKit.resolve(function)); }
    public static <P> PropertyRef persistence(Class<P> type, SFunction<P, ?> function) { return new PropertyRef(PropertySpace.PERSISTENCE, type, LambdaKit.resolve(function)); }
    public PropertySpace getSpace() { return space; } public Class<?> getOwnerType() { return ownerType; } public String getProperty() { return property; }
    public boolean isDomain() { return space == PropertySpace.DOMAIN; } public boolean isPersistence() { return space == PropertySpace.PERSISTENCE; }
}
