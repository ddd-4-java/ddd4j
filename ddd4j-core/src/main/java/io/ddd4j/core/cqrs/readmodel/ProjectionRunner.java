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

/**
 * 增量投影运行器。
 *
 * <p>封装通用投影流程：读取当前位置、拉取事件块、执行业务投影、推进位置。
 * 框架适配层只需要负责调度、事务和 Bean 装配。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class ProjectionRunner<E> {

    private final ProjectionService projectionService;

    private final EventChunkReader<E> chunkReader;

    private final ProjectionMetrics metrics;

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
        this.metrics = Objects.requireNonNullElse(metrics, NoopProjectionMetrics.INSTANCE);
    }

    /**
     * 运行单个视图的一次增量投影。
     *
     * @param view 投影视图
     * @return 本次读取出的事件块
     */
    public EventChunk<E> runOnce(ProjectionView<E> view) {
        ProjectionView<E> projectionView = validateView(view);
        String streamId = projectionView.getStreamId();
        metrics.onRunStarted(streamId);
        long startNanos = System.nanoTime();
        long previousPosition = projectionService.readProjectionPosition(streamId);
        try {
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
     * 运行多个视图的一次增量投影。
     *
     * @param views 投影视图集合
     */
    public void runAll(Collection<? extends ProjectionView<E>> views) {
        if (CollKit.isEmpty(views)) {
            return;
        }
        for (ProjectionView<E> view : views) {
            try {
                runOnce(view);
            } catch (RuntimeException ex) {
                log.error("Projection view '{}' failed, continuing with next view", view.getName(), ex);
            }
        }
    }

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
