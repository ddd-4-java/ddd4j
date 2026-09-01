package io.ddd4j.data.eventstore.jpa;

import java.io.Serializable;
import java.util.Objects;

/** {@link StoredEventEntity} 的复合主键（aggregate_type + aggregate_id + version）。 */
public class StoredEventEntityId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String aggregateType;
    private String aggregateId;
    private long version;

    public StoredEventEntityId() {
    }

    public StoredEventEntityId(String aggregateType, String aggregateId, long version) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.version = version;
    }

    public String getAggregateType() { return aggregateType; }

    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }

    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public long getVersion() { return version; }

    public void setVersion(long version) { this.version = version; }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StoredEventEntityId)) {
            return false;
        }
        StoredEventEntityId that = (StoredEventEntityId) object;
        return version == that.version
                && Objects.equals(aggregateType, that.aggregateType)
                && Objects.equals(aggregateId, that.aggregateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType, aggregateId, version);
    }
}
