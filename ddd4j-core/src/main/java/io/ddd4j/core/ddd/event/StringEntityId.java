package io.ddd4j.core.ddd.event;
import java.util.Objects;
/** 通用字符串实体标识。 */
public final class StringEntityId implements EntityId {
    private static final long serialVersionUID = 1L;
    private static final EntityType TYPE = new StringEntityType("String");
    private final String value;
    public StringEntityId(String value) { if (StringEntityType.isBlank(value)) throw new IllegalArgumentException("Entity id must not be blank"); this.value = value; }
    @Override public EntityType getType() { return TYPE; }
    @Override public String asString() { return value; }
    @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
    @Override public boolean equals(Object object) { return object instanceof StringEntityId && Objects.equals(value, ((StringEntityId) object).value); }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value; }
}
