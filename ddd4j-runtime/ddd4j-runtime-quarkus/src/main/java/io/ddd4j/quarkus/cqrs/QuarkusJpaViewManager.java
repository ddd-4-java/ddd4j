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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quarkus CQRS 读侧视图管理器（基于 {@code @Scheduled}）。
 *
 * <p>实现 ddd4j-core 的 {@link ViewManager} SPI。Quarkus 通过 CDI 事件
 * （{@link Startup} / {@link Shutdown}）自动触发 start/stop。
 *
 * <p>实际视图拉取由 {@code QuarkusJpaProjectionService}（{@code @Scheduled}）
 * 负责，本类仅负责生命周期管理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-QUARKUS : ViewManager ###")
@ApplicationScoped
public class QuarkusJpaViewManager implements ViewManager {

    /**
     * 投影位置仓储（CDI 可选注入，不可用时 getProjectionStatus 返回基线状态）
     */
    @Inject
    Instance<ProjectionPositionRepository> positionRepositories;

    /**
     * 投影运行指标（CDI 可选注入，不可用时运行时字段置空）
     */
    @Inject
    Instance<ProjectionMetrics> projectionMetricsInstances;

    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    void onStart(@Observes Startup event) {
        start();
    }

    void onStop(@Observes Shutdown event) {
        stop();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("QuarkusJpaViewManager started");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("QuarkusJpaViewManager stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void triggerOnce() {
        log.info("triggerOnce() - 业务方应在 QuarkusJpaProjectionService override");
    }

    /**
     * 查询指定投影视图的实时状态。
     *
     * <p>若 CDI 容器中存在 {@link ProjectionPositionRepository} Bean，则从仓储读取真实位置；
     * 否则返回基线状态。运行时字段（lastRunAt / lastEventCount / lastError）通过
     * {@link ProjectionMetrics#getLastRunInfo(String)} 回填；未注入时置 null / 0。
     *
     * @param streamId 投影流 ID
     * @return 投影状态快照
     * @since 3.0.x
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        if (positionRepositories.isUnsatisfied()) {
            return ProjectionStatus.baseline(streamId, isRunning());
        }
        ProjectionPositionRepository repository = positionRepositories.get();
        Optional<ProjectionPosition> position = repository.findByStreamId(streamId);
        long nextEventNumber = position.map(ProjectionPosition::getNextEventNumber).orElse(0L);
        if (projectionMetricsInstances != null && !projectionMetricsInstances.isUnsatisfied()) {
            ProjectionMetrics metrics = projectionMetricsInstances.get();
            return metrics.getLastRunInfo(streamId)
                    .map(info -> new ProjectionStatus(streamId, nextEventNumber, isRunning(),
                            info.lastRunAt(), info.lastEventCount(), info.lastError()))
                    .orElse(new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null));
        }
        return new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null);
    }
}
