package io.ddd4j.core.cqrs.readmodel;

import java.util.Objects;

/** 不可变的默认投影位置。 */
public final class DefaultProjectionPosition implements ProjectionPosition {
    private static final long serialVersionUID = 1L;
    private final String streamId; private final long nextEventNumber;
    public DefaultProjectionPosition(String streamId, long nextEventNumber) {
        if (streamId == null || streamId.trim().isEmpty()) throw new IllegalArgumentException("streamId must not be blank");
        if (nextEventNumber < 0) throw new IllegalArgumentException("nextEventNumber must not be negative");
        this.streamId = streamId; this.nextEventNumber = nextEventNumber;
    }
    public static DefaultProjectionPosition zero(String streamId) { return new DefaultProjectionPosition(streamId, 0L); }
    @Override public String getStreamId() { return streamId; }
    @Override public long getNextEventNumber() { return nextEventNumber; }
    @Override public ProjectionPosition withNextEventNumber(long nextEventNumber) { return nextEventNumber == this.nextEventNumber ? this : new DefaultProjectionPosition(streamId, nextEventNumber); }
    @Override public boolean equals(Object object) { return object instanceof ProjectionPosition && Objects.equals(streamId, ((ProjectionPosition) object).getStreamId()) && nextEventNumber == ((ProjectionPosition) object).getNextEventNumber(); }
    @Override public int hashCode() { return Objects.hash(streamId, nextEventNumber); }
}
