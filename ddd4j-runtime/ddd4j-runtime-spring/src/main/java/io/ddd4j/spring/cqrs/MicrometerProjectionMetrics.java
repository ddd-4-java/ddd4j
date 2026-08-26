/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.constant.ProjectionConstants;
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
 * 基于 Micrometer 的投影运行指标适配器。
 *
 * <p>将 {@link ProjectionMetrics} 回调桥接到 Micrometer {@link MeterRegistry}，
 * 记录以下指标：
 * <ul>
 *   <li>{@code projection.events.total}（Counter）：已处理事件总数，tag {@code stream}</li>
 *   <li>{@code projection.run.duration}（Timer）：单次运行耗时，tag {@code stream}</li>
 *   <li>{@code projection.errors.total}（Counter）：运行失败次数，tag {@code stream}</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * // Spring Bean 配置
 * @Bean
 * public ProjectionMetrics projectionMetrics(MeterRegistry registry) {
 *     return new MicrometerProjectionMetrics(registry);
 * }
 * }</pre>
 *
 * <p>依赖 {@code io.micrometer:micrometer-core}（版本由 ddd4j-dependencies BOM 管理）。
 * 该依赖声明为 {@code optional}，不会传递到业务项目。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public class MicrometerProjectionMetrics implements ProjectionMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, ProjectionRunInfo> runInfoMap = new ConcurrentHashMap<>();

    /**
     * 构造 Micrometer 投影指标适配器。
     *
     * @param registry Micrometer MeterRegistry；不允许 null
     * @throws NullPointerException registry 为 null 时抛出
     */
    public MicrometerProjectionMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry must not be null");
    }

    @Override
    public void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
        Counter.builder(ProjectionConstants.METRIC_EVENTS_TOTAL)
                .tag(ProjectionConstants.TAG_STREAM, streamId)
                .description("Total number of projected events")
                .register(registry)
                .increment(eventCount);

        Timer.builder(ProjectionConstants.METRIC_RUN_DURATION)
                .tag(ProjectionConstants.TAG_STREAM, streamId)
                .description("Projection run duration")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        runInfoMap.put(streamId, new ProjectionRunInfo(Instant.now(), eventCount, null));
    }

    @Override
    public void onRunFailed(String streamId, Throwable error) {
        Counter.builder(ProjectionConstants.METRIC_ERRORS_TOTAL)
                .tag(ProjectionConstants.TAG_STREAM, streamId)
                .description("Total number of projection run failures")
                .register(registry)
                .increment();

        runInfoMap.merge(streamId,
                new ProjectionRunInfo(Instant.now(), 0, error.getMessage()),
                (old, newInfo) -> newInfo);
    }

    @Override
    public Optional<ProjectionRunInfo> getLastRunInfo(String streamId) {
        return Optional.ofNullable(runInfoMap.get(streamId));
    }
}
