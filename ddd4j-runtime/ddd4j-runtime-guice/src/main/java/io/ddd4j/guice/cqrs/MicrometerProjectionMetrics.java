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
package io.ddd4j.guice.cqrs;

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
 * 基于 Micrometer 的投影运行指标适配器（Guice 运行时）。
 *
 * <p>将 {@link ProjectionMetrics} 回调桥接到 Micrometer {@link MeterRegistry}，
 * 记录以下指标：
 * <ul>
 *   <li>{@code projection.events.total}（Counter）：已处理事件总数，tag {@code stream}</li>
 *   <li>{@code projection.run.duration}（Timer）：单次运行耗时，tag {@code stream}</li>
 *   <li>{@code projection.errors.total}（Counter）：运行失败次数，tag {@code stream}</li>
 * </ul>
 *
 * <p>使用方式（Guice Module 配置）：
 * <pre>{@code
 * bind(ProjectionMetrics.class).toInstance(new MicrometerProjectionMetrics(registry));
 * }</pre>
 *
 * <p>依赖 {@code io.micrometer:micrometer-core}（版本由 ddd4j-dependencies BOM 管理）。
 * 该依赖声明为 {@code optional}，不会传递到业务项目。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public class MicrometerProjectionMetrics implements ProjectionMetrics {

    /** \u6ce8\u518c\u548c\u8bfb\u53d6\u5f53\u524d\u7ec4\u4ef6 Micrometer \u6307\u6807\u7684\u6ce8\u518c\u8868\u3002 */
    private final MeterRegistry registry;
    /** \u6309\u4e8b\u4ef6\u6d41\u6807\u8bc6\u4fdd\u5b58\u6700\u8fd1\u4e00\u6b21\u6295\u5f71\u8fd0\u884c\u7ed3\u679c\u3002 */
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

    /**
     * \u8bb0\u5f55\u4e00\u6b21\u6295\u5f71\u6210\u529f\u6267\u884c\u7684\u4e8b\u4ef6\u8ba1\u6570\u548c\u8017\u65f6\uff0c\u5e76\u66f4\u65b0\u6700\u8fd1\u6267\u884c\u4fe1\u606f\u3002
     * @param streamId \u6295\u5f71\u4e8b\u4ef6\u6d41\u6807\u8bc6
     * @param eventCount \u672c\u6b21\u5904\u7406\u7684\u4e8b\u4ef6\u6570
     * @param durationNanos \u672c\u6b21\u6295\u5f71\u6267\u884c\u8017\u65f6\uff0c\u5355\u4f4d\u4e3a\u7eb3\u79d2
     * @param positionAdvance \u6295\u5f71\u6267\u884c\u5668\u63d0\u4f9b\u7684\u4e8b\u4ef6\u6d41\u4f4d\u70b9\u63a8\u8fdb\u91cf
     */
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

    /**
     * \u8bb0\u5f55\u4e8b\u4ef6\u6d41\u7684\u4e00\u6b21\u6295\u5f71\u6267\u884c\u5931\u8d25\u3002
     * @param streamId \u6295\u5f71\u4e8b\u4ef6\u6d41\u6807\u8bc6
     * @param error \u9020\u6210\u6267\u884c\u5931\u8d25\u7684\u5f02\u5e38
     */
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

    /**
     * 读取投影最近一次运行的记录。
     *
     * <p>此实现调用 <code>Optional.ofNullable(runInfoMap.get(streamId))</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 最近一次运行的时间和结果等信息
     */
    @Override
    public Optional<ProjectionRunInfo> getLastRunInfo(String streamId) {
        return Optional.ofNullable(runInfoMap.get(streamId));
    }
}
