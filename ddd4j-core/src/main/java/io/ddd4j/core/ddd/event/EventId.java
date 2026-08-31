package io.ddd4j.core.ddd.event;
import java.io.Serializable;
import java.util.UUID;
/** 全局唯一事件标识。 */
public final class EventId implements Serializable {
    private static final long serialVersionUID = 1L;
    private final UUID value;
    public EventId() { this(UUID.randomUUID()); }
    public EventId(UUID value) { if (value == null) throw new NullPointerException("value must not be null"); this.value = value; }
    public static EventId valueOf(String value) { return StringEntityType.isBlank(value) ? null : new EventId(UUID.fromString(value)); }
    public UUID asUuid() { return value; }
    public String asString() { return value.toString(); }
    @Override public boolean equals(Object object) { return object instanceof EventId && value.equals(((EventId) object).value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return asString(); }
}
