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

import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring CQRS 读侧视图管理器（基于 {@link TaskScheduler}）。
 *
 * <p>实现 ddd4j-core 的 {@link ViewManager} SPI，封装 Spring 的
 * {@code ScheduledTaskRegistrar}，按 CRON 表达式定时触发视图增量拉取。
 *
 * <p>由 {@code ddd4j-boot-ddd-autoconfigure} 注册为 Spring Bean，业务项目无需关心。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-SPRING : ViewManager ###")
public class SpringJpaViewManager implements ViewManager {

    /**
     * 视图调度器
     */
    private final ViewScheduler scheduler;
    /**
     * 投影位置仓储（可选，用于查询真实投影位置）
     */
    private final ProjectionPositionRepository positionRepository;
    /**
     * 投影运行指标（可选，用于回填运行时状态字段）
     */
    private final ProjectionMetrics projectionMetrics;
    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 已调度的视图任务句柄集
     */
    private final ConcurrentMap<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    /**
     * 构造视图管理器（无位置仓储，getProjectionStatus 返回基线状态）。
     *
     * @param scheduler 视图调度器
     */
    public SpringJpaViewManager(ViewScheduler scheduler) {
        this(scheduler, null, null);
    }

    /**
     * 构造视图管理器（带位置仓储，getProjectionStatus 返回真实位置）。
     *
     * @param scheduler          视图调度器
     * @param positionRepository 投影位置仓储；为 null 时 getProjectionStatus 返回基线状态
     */
    public SpringJpaViewManager(ViewScheduler scheduler, ProjectionPositionRepository positionRepository) {
        this(scheduler, positionRepository, null);
    }

    /**
     * 构造视图管理器（带位置仓储 + 运行指标，getProjectionStatus 返回完整状态）。
     *
     * @param scheduler          视图调度器
     * @param positionRepository 投影位置仓储；为 null 时 getProjectionStatus 返回基线状态
     * @param projectionMetrics  投影运行指标；为 null 时运行时字段（lastRunAt 等）置空
     */
    public SpringJpaViewManager(ViewScheduler scheduler, ProjectionPositionRepository positionRepository,
                                ProjectionMetrics projectionMetrics) {
        this.scheduler = scheduler;
        this.positionRepository = positionRepository;
        this.projectionMetrics = projectionMetrics;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("SpringJpaViewManager started");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            handles.values().forEach(ViewScheduler.ViewScheduleHandle::cancel);
            handles.clear();
            log.info("SpringJpaViewManager stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void triggerOnce() {
        // 业务方可在测试或运维场景调用，由具体 View 实现增量拉取
        log.info("triggerOnce() - 业务方应在子类 override");
    }

    /**
     * 查询指定投影视图的实时状态。
     *
     * <p>若构造时注入了 {@link ProjectionPositionRepository}，则从仓储读取真实位置；
     * 否则返回基线状态。运行时字段（lastRunAt / lastEventCount / lastError）通过
     * {@link ProjectionMetrics#getLastRunInfo(String)} 回填；未注入时置 null / 0。
     *
     * @param streamId 投影流 ID
     * @return 投影状态快照
     * @since 3.0.x
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        if (positionRepository == null) {
            return ProjectionStatus.baseline(streamId, isRunning());
        }
        Optional<ProjectionPosition> position = positionRepository.findByStreamId(streamId);
        long nextEventNumber = position.map(ProjectionPosition::getNextEventNumber).orElse(0L);
        if (projectionMetrics != null) {
            return projectionMetrics.getLastRunInfo(streamId)
                    .map(info -> new ProjectionStatus(streamId, nextEventNumber, isRunning(),
                            info.lastRunAt(), info.lastEventCount(), info.lastError()))
                    .orElse(new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null));
        }
        return new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null);
    }

    /**
     * 注册一个 CRON 调度的视图拉取任务。
     *
     * <p>供 {@code SpringEventHandlerRegistry} 在发现 View Bean 时调用：
     * <pre>{@code
     * springJpaViewManager.schedule("order-list-view", "0/5 * * * * ?", () -> view.update());
     * }</pre>
     */
    public ViewScheduler.ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(viewName, cron, task);
        handles.put(viewName, handle);
        return handle;
    }

    /**
     * Spring 上下文刷新完成后自动启动。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        start();
    }
}
