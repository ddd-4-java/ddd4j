package io.ddd4j.core.cqrs.readmodel;

import java.time.Instant;
import java.util.Objects;

/**
 * 投影最近一次运行的快照信息（由 {@link ProjectionMetrics} 实现方记录）。
 *
 * @param lastRunAt      上次运行完成时间
 * @param lastEventCount 上次运行处理的事件数量
 * @param lastError      上次运行失败的错误信息（成功时为 null）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class ProjectionRunInfo {

    private final Instant lastRunAt;
    private final int lastEventCount;
    private final String lastError;

    public ProjectionRunInfo(Instant lastRunAt, int lastEventCount, String lastError) {
        this.lastRunAt = lastRunAt;
        this.lastEventCount = lastEventCount;
        this.lastError = lastError;
    }

    public Instant getLastRunAt() { return lastRunAt; }
    public int getLastEventCount() { return lastEventCount; }
    public String getLastError() { return lastError; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectionRunInfo)) return false;
        ProjectionRunInfo that = (ProjectionRunInfo) o;
        return lastEventCount == that.lastEventCount
                && Objects.equals(lastRunAt, that.lastRunAt)
                && Objects.equals(lastError, that.lastError);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(lastRunAt);
        result = 31 * result + lastEventCount;
        result = 31 * result + Objects.hashCode(lastError);
        return result;
    }

    @Override
    public String toString() {
        return "ProjectionRunInfo{lastRunAt=" + lastRunAt + ", lastEventCount=" + lastEventCount
                + ", lastError=" + lastError + '}';
    }
}
