package io.ddd4j.core.cqrs.readmodel;

import java.time.Instant;
import java.util.Objects;

/** 投影运行状态快照（回填自 3.0.x fbada828，适配 Java 8 基线）。 */
public final class ProjectionStatus {
    private final String streamId;
    private final long nextEventNumber;
    private final boolean running;
    private final Instant lastRunAt;
    private final int lastEventCount;
    private final String lastError;
    public ProjectionStatus(String streamId, long nextEventNumber, boolean running, Instant lastRunAt, int lastEventCount, String lastError) {
        this.streamId = Objects.requireNonNull(streamId, "streamId must not be null");
        if (nextEventNumber < 0) throw new IllegalArgumentException("nextEventNumber must not be negative");
        if (lastEventCount < 0) throw new IllegalArgumentException("lastEventCount must not be negative");
        this.nextEventNumber = nextEventNumber; this.running = running; this.lastRunAt = lastRunAt; this.lastEventCount = lastEventCount; this.lastError = lastError;
    }
    /** 创建基线状态（nextEventNumber=0，无运行历史）。 */
    public static ProjectionStatus baseline(String streamId, boolean running) { return new ProjectionStatus(streamId, 0L, running, null, 0, null); }
    public String getStreamId() { return streamId; }
    public long getNextEventNumber() { return nextEventNumber; }
    public boolean isRunning() { return running; }
    public Instant getLastRunAt() { return lastRunAt; }
    public int getLastEventCount() { return lastEventCount; }
    public String getLastError() { return lastError; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectionStatus)) return false;
        ProjectionStatus that = (ProjectionStatus) o;
        return nextEventNumber == that.nextEventNumber && running == that.running && lastEventCount == that.lastEventCount
                && streamId.equals(that.streamId) && Objects.equals(lastRunAt, that.lastRunAt) && Objects.equals(lastError, that.lastError);
    }
    @Override public int hashCode() { return Objects.hash(streamId, nextEventNumber, running, lastRunAt, lastEventCount, lastError); }
    @Override public String toString() { return "ProjectionStatus{streamId=" + streamId + ", nextEventNumber=" + nextEventNumber + ", running=" + running + ", lastRunAt=" + lastRunAt + ", lastEventCount=" + lastEventCount + ", lastError=" + lastError + "}"; }
}
