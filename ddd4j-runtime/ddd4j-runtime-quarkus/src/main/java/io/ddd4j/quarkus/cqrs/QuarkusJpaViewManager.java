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
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quarkus CQRS 读侧视图管理器（基于 {@code @Scheduled}）。
 *
 * <p>实现 ddd4j-core 的 {@link ViewManager} SPI。Quarkus 通过 CDI 事件
 * （{@link StartupEvent} / {@link ShutdownEvent}）自动触发 start/stop。
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
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
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
     * \u5728 Quarkus \u542f\u52a8\u540e\u9009\u62e9\u5e76\u542f\u52a8\u6295\u5f71\u8fd0\u884c\u6a21\u5f0f\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void onStart(@Observes StartupEvent event) {
        start();
    }

    /**
     * \u5728\u505c\u6b62\u4e8b\u4ef6\u4e2d\u6e05\u7406\u5f53\u524d\u7ba1\u7406\u5668\u542f\u52a8\u7684\u6295\u5f71\u4efb\u52a1\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void onStop(@Observes ShutdownEvent event) {
        stop();
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>JPA 实现调用 <code>running.compareAndSet(false, true)</code>。</p>
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("QuarkusJpaViewManager started");
        }
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>JPA 实现调用 <code>running.compareAndSet(true, false)</code>。</p>
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("QuarkusJpaViewManager stopped");
        }
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>JPA 实现调用 <code>running.get()</code>。</p>
     *
     * @return 实际返回<code>running.get()</code>
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>JPA 实现调用 <code>log.info("triggerOnce() - \u4e1a\u52a1\u65b9\u5e94\u5728 QuarkusJpaProjectionService override")</code>。</p>
     */
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
