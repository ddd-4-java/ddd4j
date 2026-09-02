package io.ddd4j.monitor.metrics;

import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer 投影指标适配器（回填自 3.0.x 5e5e70e3，适配 1.0.x 包结构）。
 *
 * <p>实现 {@link ProjectionMetrics} 端口，将投影运行指标暴露到任意
 * Micrometer {@link MeterRegistry}（Simple/Prometheus/OTel 网关均可）。
 * 指标名复用 {@code ddd4j.core.cqrs.readmodel.ProjectionConstants}。
 */
public class MicrometerProjectionMetrics implements ProjectionMetrics {
    private final MeterRegistry registry;
    private final ConcurrentMap<String, ProjectionRunInfo> runInfoMap = new ConcurrentHashMap<String, ProjectionRunInfo>();

    /**
     * @param registry Micrometer MeterRegistry；不允许 null
     * @throws NullPointerException registry 为 null 时抛出
     */
    public MicrometerProjectionMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry must not be null");
    }

    @Override
    public void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
        Counter.builder(io.ddd4j.core.cqrs.readmodel.ProjectionConstants.METRIC_EVENTS_TOTAL)
                .tag(io.ddd4j.core.cqrs.readmodel.ProjectionConstants.TAG_STREAM, streamId)
                .description("Total number of projected events")
                .register(registry)
                .increment(eventCount);
        Timer.builder(io.ddd4j.core.cqrs.readmodel.ProjectionConstants.METRIC_RUN_DURATION)
                .tag(io.ddd4j.core.cqrs.readmodel.ProjectionConstants.TAG_STREAM, streamId)
                .description("Projection run duration")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        runInfoMap.put(streamId, new ProjectionRunInfo(Instant.now(), eventCount, null));
    }

    @Override
    public void onRunFailed(String streamId, Throwable error) {
        Counter.builder(io.ddd4j.core.cqrs.readmodel.ProjectionConstants.METRIC_ERRORS_TOTAL)
                .tag(io.ddd4j.core.cqrs.readmodel.ProjectionConstants.TAG_STREAM, streamId)
                .description("Total number of projection run failures")
                .register(registry)
                .increment();
        runInfoMap.put(streamId, new ProjectionRunInfo(Instant.now(), 0, error.getMessage()));
    }

    @Override
    public Optional<ProjectionRunInfo> getLastRunInfo(String streamId) {
        return Optional.ofNullable(runInfoMap.get(streamId));
    }
}
