package io.ddd4j.core.ddd.event;
import java.util.Objects;
/** 基于字符串的实体类型值对象。 */
public final class StringEntityType implements EntityType {
    private static final long serialVersionUID = 1L;
    private final String value;
    public StringEntityType(String value) { if (isBlank(value)) throw new IllegalArgumentException("Entity type must not be blank"); this.value = value; }
    @Override public String asString() { return value; }
    @Override public boolean equals(Object object) { return object instanceof StringEntityType && Objects.equals(value, ((StringEntityType) object).value); }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value; }
    static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
