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
package io.ddd4j.metrics;

import io.ddd4j.core.constant.ProjectionConstants;
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.kit.text.StrPool;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.OpenTelemetry;

import java.util.Objects;

/**
 * 基于 OpenTelemetry 的投影运行指标适配器。
 *
 * <p>将 {@link ProjectionMetrics} 回调桥接到 OTel {@link Meter}，
 * 记录以下指标：
 * <ul>
 *   <li>{@code ddd4j.projection.run.count}（LongCounter）：运行次数，attribute {@code streamId}</li>
 *   <li>{@code ddd4j.projection.event.count}（LongCounter）：已处理事件总数，attribute {@code streamId}</li>
 *   <li>{@code ddd4j.projection.run.duration}（DoubleHistogram）：单次运行耗时（毫秒），attribute {@code streamId}</li>
 *   <li>{@code ddd4j.projection.run.error}（LongCounter）：运行失败次数，attribute {@code streamId}</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * // 直接注入 Meter（推荐，最可测）
 * Meter meter = OpenTelemetry.noop().getMeter("my-app");
 * ProjectionMetrics metrics = new OpenTelemetryProjectionMetrics(meter);
 *
 * // 或使用便捷构造
 * ProjectionMetrics metrics = new OpenTelemetryProjectionMetrics(OpenTelemetry.noop(), "my-app");
 * }</pre>
 *
 * <p>仅依赖 {@code io.opentelemetry:opentelemetry-api}（版本由 ddd4j-dependencies BOM 管理），
 * 不依赖 OpenTelemetry SDK。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public class OpenTelemetryProjectionMetrics implements ProjectionMetrics {

    /** streamId attribute key。 */
    private static final AttributeKey<String> STREAM_ID = AttributeKey.stringKey(ProjectionConstants.OTel_ATTR_STREAM_ID);

    /** 运行次数指标名称。 */
    static final String METRIC_RUN_COUNT = ProjectionConstants.OTel_METRIC_RUN_COUNT;
    /** 事件计数指标名称。 */
    static final String METRIC_EVENT_COUNT = ProjectionConstants.OTel_METRIC_EVENT_COUNT;
    /** 运行耗时指标名称（毫秒）。 */
    static final String METRIC_RUN_DURATION = ProjectionConstants.OTel_METRIC_RUN_DURATION;
    /** 运行错误指标名称。 */
    static final String METRIC_RUN_ERROR = ProjectionConstants.OTel_METRIC_RUN_ERROR;

    private final LongCounter runCounter;
    private final LongCounter eventCounter;
    private final DoubleHistogram durationHistogram;
    private final LongCounter errorCounter;

    /**
     * 构造 OpenTelemetry 投影指标适配器。
     *
     * @param meter OTel Meter；不允许 null
     * @throws NullPointerException meter 为 null 时抛出
     */
    public OpenTelemetryProjectionMetrics(Meter meter) {
        Objects.requireNonNull(meter, "Meter must not be null");
        this.runCounter = meter.counterBuilder(METRIC_RUN_COUNT)
                .setDescription("Number of projection runs")
                .build();
        this.eventCounter = meter.counterBuilder(METRIC_EVENT_COUNT)
                .setDescription("Total number of projected events")
                .build();
        this.durationHistogram = meter.histogramBuilder(METRIC_RUN_DURATION)
                .setDescription("Projection run duration in milliseconds")
                .setUnit(StrPool.MS)
                .build();
        this.errorCounter = meter.counterBuilder(METRIC_RUN_ERROR)
                .setDescription("Number of projection run failures")
                .build();
    }

    /**
     * 便捷构造：从 {@link OpenTelemetry} 实例获取 Meter。
     *
     * @param openTelemetry OTel 实例；不允许 null
     * @param instrumentationScope 仪表化作用域名称；不允许 null
     * @throws NullPointerException 任一参数为 null 时抛出
     */
    public OpenTelemetryProjectionMetrics(OpenTelemetry openTelemetry, String instrumentationScope) {
        this(Objects.requireNonNull(openTelemetry, "OpenTelemetry must not be null")
                .getMeter(Objects.requireNonNull(instrumentationScope, "instrumentationScope must not be null")));
    }

    @Override
    public void onRunStarted(String streamId) {
        runCounter.add(1, Attributes.of(STREAM_ID, streamId));
    }

    @Override
    public void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
        Attributes attrs = Attributes.of(STREAM_ID, streamId);
        eventCounter.add(eventCount, attrs);
        durationHistogram.record(durationNanos / 1_000_000.0, attrs);
    }

    @Override
    public void onRunFailed(String streamId, Throwable error) {
        errorCounter.add(1, Attributes.of(STREAM_ID, streamId));
    }
}
