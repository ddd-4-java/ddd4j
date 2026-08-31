package io.ddd4j.core.cqrs.readmodel;

import java.time.Instant;

/** 投影最近一次运行状态快照。 */
public final class ProjectionRunInfo {
    private final Instant lastRunAt; private final int lastEventCount; private final String lastError;
    public ProjectionRunInfo(Instant lastRunAt, int lastEventCount, String lastError) { this.lastRunAt = lastRunAt; this.lastEventCount = lastEventCount; this.lastError = lastError; }
    public Instant getLastRunAt() { return lastRunAt; } public int getLastEventCount() { return lastEventCount; } public String getLastError() { return lastError; }
}
