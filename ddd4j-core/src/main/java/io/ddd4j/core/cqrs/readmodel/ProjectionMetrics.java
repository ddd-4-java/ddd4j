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
package io.ddd4j.core.cqrs.readmodel;

import java.time.Instant;
import java.util.Optional;

/**
 * 投影运行指标 SPI（纯 Java，零外部依赖）。
 *
 * <p>所有方法均提供 {@code default} 空实现（no-op），实现方可按需覆盖。
 * 运行时适配层（如 Micrometer / OpenTelemetry）通过实现本接口注入指标采集逻辑。
 *
 * <h3>对接示例（Micrometer）</h3>
 * <pre>{@code
 * public class MicrometerProjectionMetrics implements ProjectionMetrics {
 *     private final MeterRegistry registry;
 *
 *     @Override
 *     public void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
 *         registry.counter("projection.events", "stream", streamId).increment(eventCount);
 *         registry.timer("projection.duration", "stream", streamId).record(durationNanos, TimeUnit.NANOSECONDS);
 *     }
 *
 *     @Override
 *     public void onRunFailed(String streamId, Throwable error) {
 *         registry.counter("projection.errors", "stream", streamId).increment();
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface ProjectionMetrics {

    /**
     * 投影运行开始回调。
     *
     * @param streamId 投影流 ID
     */
    default void onRunStarted(String streamId) {
        // no-op
    }

    /**
     * 投影运行成功回调。
     *
     * @param streamId        投影流 ID
     * @param eventCount      本次处理的事件数量
     * @param durationNanos   本次运行耗时（纳秒）
     * @param positionAdvance 本次位置推进量（nextEventNumber - previousEventNumber）
     */
    default void onRunCompleted(String streamId, int eventCount, long durationNanos, long positionAdvance) {
        // no-op
    }

    /**
     * 投影运行失败回调。
     *
     * @param streamId 投影流 ID
     * @param error    导致失败的异常
     */
    default void onRunFailed(String streamId, Throwable error) {
        // no-op
    }

    /**
     * 查询指定流的最近一次运行状态。
     *
     * <p>默认返回空 Optional（表示该实现不跟踪状态）。
     * 实现方可在 {@link #onRunCompleted} / {@link #onRunFailed} 中记录运行时信息，
     * 供 {@link ViewManager#getProjectionStatus(String)} 回填。
     *
     * @param streamId 投影流 ID
     * @return 最近一次运行信息；不跟踪时返回 {@code Optional.empty()}
     */
    default Optional<ProjectionRunInfo> getLastRunInfo(String streamId) {
        return Optional.empty();
    }
}
