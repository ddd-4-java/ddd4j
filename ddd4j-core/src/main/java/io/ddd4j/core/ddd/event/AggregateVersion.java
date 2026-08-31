package io.ddd4j.core.ddd.event;
import java.io.Serializable;
/** 聚合事件流版本值对象。 */
public final class AggregateVersion implements Serializable {
    private static final long serialVersionUID = 1L;
    private final long value;
    public AggregateVersion(long value) { if (value < 0) throw new IllegalArgumentException("Aggregate version must not be negative"); this.value = value; }
    public long asLong() { return value; }
    public int asInt() { return (int) value; }
    @Override public boolean equals(Object object) { return object instanceof AggregateVersion && value == ((AggregateVersion) object).value; }
    @Override public int hashCode() { return Long.valueOf(value).hashCode(); }
    @Override public String toString() { return String.valueOf(value); }
}
