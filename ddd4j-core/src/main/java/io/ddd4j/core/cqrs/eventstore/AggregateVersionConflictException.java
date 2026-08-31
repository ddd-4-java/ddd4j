package io.ddd4j.core.cqrs.eventstore;

/** 乐观锁版本冲突。 */
public class AggregateVersionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String aggregateType; private final String aggregateId; private final long expectedVersion; private final long actualVersion;
    public AggregateVersionConflictException(String aggregateType, String aggregateId, long expectedVersion, long actualVersion) {
        super("Aggregate " + aggregateType + "#" + aggregateId + " version conflict: expected=" + expectedVersion + ", actual=" + actualVersion);
        this.aggregateType = aggregateType; this.aggregateId = aggregateId; this.expectedVersion = expectedVersion; this.actualVersion = actualVersion;
    }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public long expectedVersion() { return expectedVersion; }
    public long actualVersion() { return actualVersion; }
}
