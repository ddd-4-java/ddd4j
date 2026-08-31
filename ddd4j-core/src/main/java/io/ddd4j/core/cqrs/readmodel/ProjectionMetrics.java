package io.ddd4j.core.cqrs.readmodel;

import java.util.Optional;

/** 投影运行指标和熔断信号端口。 */
public interface ProjectionMetrics {
    default void onRunStarted(String streamId) { }
    default void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) { }
    default void onRunFailed(String streamId, Throwable error) { }
    default Optional<ProjectionRunInfo> getLastRunInfo(String streamId) { return Optional.empty(); }
    default void onCircuitOpened(String viewName, int consecutiveFailures) { }
}
