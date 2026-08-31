package io.ddd4j.core.ddd.event;
import java.io.Serializable;
import java.util.Objects;
/** 领域事件类型的稳定标识。 */
public final class EventType implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String value;
    public EventType(String value) { if (StringEntityType.isBlank(value) || value.length() > 255) throw new IllegalArgumentException("Event type must contain 1 to 255 characters"); this.value = value; }
    public String asString() { return value; }
    @Override public boolean equals(Object object) { return object instanceof EventType && Objects.equals(value, ((EventType) object).value); }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value; }
}
