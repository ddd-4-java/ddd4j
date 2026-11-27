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

import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 增量投影运行器。
 *
 * <p>封装通用投影流程：读取当前位置、拉取事件块、执行业务投影、推进位置。
 * 框架适配层只需要负责调度、事务和 Bean 装配。
 *
 * <p><b>异常暴露</b></p>
 * <p>{@link #runAll} 保持失败即传播；{@link #runAllIsolated} 在视图间隔离异常，且对连续失败计数：
 * <ul>
 *   <li>单次失败 → WARN 级日志；视图状态由 {@link ProjectionMetrics#getLastRunInfo} 暴露</li>
 *   <li>同一视图连续失败达到 {@link #CONSECUTIVE_FAILURE_THRESHOLD}（默认 5）→ ERROR 级日志 + 发出
 *       {@link ProjectionMetrics#onCircuitOpened(String, int)} 熔断信号，外部可据此触发告警</li>
 *   <li>视图恢复一次成功即重置计数</li>
 * </ul>
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class ProjectionRunner<E> {

    /**
     * 同一视图连续失败触发熔断信号的阈值。
     */
    public static final int CONSECUTIVE_FAILURE_THRESHOLD = 5;

    /** \u5e94\u7528\u6295\u5f71\u4e8b\u4ef6\u53ca\u7ef4\u62a4\u68c0\u67e5\u70b9\u7684\u5171\u4eab\u670d\u52a1\u3002 */
    private final ProjectionService projectionService;

    /** \u6309\u4f4d\u70b9\u5206\u5757\u8bfb\u53d6\u4e8b\u4ef6\u7684\u6295\u5f71\u7ec4\u4ef6\u3002 */
    private final EventChunkReader<E> chunkReader;

    /** \u63a5\u6536\u6295\u5f71\u6267\u884c\u7ed3\u679c\u548c\u5904\u7406\u8017\u65f6\u7684\u6307\u6807\u7ec4\u4ef6\u3002 */
    private final ProjectionMetrics metrics;

    /**
     * 视图名 → 连续失败计数（视图恢复成功后清零）。
     */
    private final ConcurrentMap<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    /**
     * 构造投影运行器（无指标采集）。
     *
     * @param projectionService 投影位置服务
     * @param chunkReader       事件块读取器
     */
    public ProjectionRunner(ProjectionService projectionService, EventChunkReader<E> chunkReader) {
        this(projectionService, chunkReader, NoopProjectionMetrics.INSTANCE);
    }

    /**
     * 构造投影运行器（带可选指标采集）。
     *
     * @param projectionService 投影位置服务
     * @param chunkReader       事件块读取器
     * @param metrics           投影指标回调；为 null 时使用 {@link NoopProjectionMetrics#INSTANCE}
     */
    public ProjectionRunner(ProjectionService projectionService, EventChunkReader<E> chunkReader,
                            ProjectionMetrics metrics) {
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService must not be null");
        this.chunkReader = Objects.requireNonNull(chunkReader, "chunkReader must not be null");
        this.metrics = (metrics != null ? metrics : NoopProjectionMetrics.INSTANCE);
    }

    /**
     * \u8fd0\u884c\u5355\u4e2a\u89c6\u56fe\u7684\u4e00\u6b21\u589e\u91cf\u6295\u5f71\u3002
     * @param view \u6295\u5f71\u89c6\u56fe
     * @return \u672c\u6b21\u8bfb\u53d6\u51fa\u7684\u4e8b\u4ef6\u5757
     *
     * @throws IllegalStateException \u540c\u4e00\u8fd0\u884c\u5668\u4e2d\u8be5\u6d41\u5df2\u6709\u6267\u884c\uff0c\u8c03\u7528\u65b9\u53ef\u7a0d\u540e\u91cd\u8bd5
     */
    public EventChunk<E> runOnce(ProjectionView<E> view) {
        ProjectionView<E> projectionView = validateView(view);
        String streamId = projectionView.getStreamId();
        metrics.onRunStarted(streamId);
        long startNanos = System.nanoTime();
        try {
            long previousPosition = projectionService.readProjectionPosition(streamId);
            EventChunk<E> chunk = chunkReader.read(
                    streamId,
                    previousPosition,
                    projectionView.getChunkSize(),
                    projectionView.getEventTypes()
            );
            EventChunk<E> safeChunk = Objects.requireNonNull(chunk, "chunkReader must not return null");
            if (safeChunk.hasEvents()) {
                projectionView.handleEvents(safeChunk.getEvents());
            }
            long positionAdvance = 0;
            if (safeChunk.getNextEventNumber() > previousPosition) {
                positionAdvance = safeChunk.getNextEventNumber() - previousPosition;
                projectionService.updateProjectionPosition(streamId, safeChunk.getNextEventNumber());
            }
            long durationNanos = System.nanoTime() - startNanos;
            metrics.onRunCompleted(streamId, safeChunk.getEvents().size(), durationNanos, positionAdvance);
            return safeChunk;
        } catch (RuntimeException ex) {
            metrics.onRunFailed(streamId, ex);
            throw ex;
        }
    }

    /**
     * 运行多个视图的一次增量投影，遇到异常立即向调用方传播。
     *
     * @param views 投影视图集合
     */
    public void runAll(Collection<? extends ProjectionView<E>> views) {
        if (CollKit.isEmpty(views)) {
            return;
        }
        for (ProjectionView<E> view : views) {
            runOnce(view);
        }
    }

    /**
     * 运行多个视图的一次增量投影，并隔离单个视图失败。
     *
     * <p>连续失败次数通过 {@link ProjectionMetrics#getLastRunInfo}（按 streamId）查询；
     * 同一 stream 连续 {@value #CONSECUTIVE_FAILURE_THRESHOLD} 次失败后，发出
     * {@link ProjectionMetrics#onCircuitOpened(String, int)} 信号。
     *
     * @param views 投影视图集合
     */
    public void runAllIsolated(Collection<? extends ProjectionView<E>> views) {
        if (CollKit.isEmpty(views)) {
            return;
        }
        for (ProjectionView<E> view : views) {
            String viewName = view.getName();
            try {
                runOnce(view);
                // 成功则重置连续失败计数
                consecutiveFailures.remove(viewName);
            } catch (RuntimeException ex) {
                AtomicInteger counter = consecutiveFailures.computeIfAbsent(viewName, k -> new AtomicInteger());
                int failures = counter.incrementAndGet();
                if (failures >= CONSECUTIVE_FAILURE_THRESHOLD && failures % CONSECUTIVE_FAILURE_THRESHOLD == 0) {
                    try {
                        metrics.onCircuitOpened(viewName, failures);
                    } catch (RuntimeException callbackFailure) {
                        log.warn("Projection circuit callback failed for view {}", viewName, callbackFailure);
                    }
                }
            }
        }
    }

    /**
     * \u5728\u8fd0\u884c\u524d\u68c0\u67e5\u89c6\u56fe\u53ca\u5176\u4e8b\u4ef6\u6d41\u540d\u79f0\u3002
     * @param view \u8bf7\u6c42\u6267\u884c\u7684\u6295\u5f71\u89c6\u56fe
     * @return \u6821\u9a8c\u901a\u8fc7\u7684\u6295\u5f71\u89c6\u56fe
     */
    private ProjectionView<E> validateView(ProjectionView<E> view) {
        ProjectionView<E> projectionView = Objects.requireNonNull(view, "view must not be null");
        if (StrKit.isBlank(projectionView.getName())) {
            throw new IllegalArgumentException("view name must not be blank");
        }
        if (StrKit.isBlank(projectionView.getStreamId())) {
            throw new IllegalArgumentException("view streamId must not be blank");
        }
        if (projectionView.getChunkSize() <= 0) {
            throw new IllegalArgumentException("view chunkSize must be positive");
        }
        return projectionView;
    }
}
