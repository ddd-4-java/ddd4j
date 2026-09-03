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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.ddd4j.guice.GuiceConstants;
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guice runtime default CQRS read-side view manager.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class GuiceViewManager implements ViewManager, ViewScheduler, AutoCloseable {

    /** default thread pool size */
    private static final int DEFAULT_THREAD_POOL_SIZE = GuiceConstants.DEFAULT_THREAD_POOL_SIZE;

    /** projection position repository (optional, for real status queries) */
    private final ProjectionPositionRepository positionRepository;
    /** projection metrics (optional, for backfilling runtime status fields) */
    private final ProjectionMetrics projectionMetrics;
    /** running state flag */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** scheduled view task handle map */
    private final ConcurrentMap<String, ScheduledFuture<?>> handles = new ConcurrentHashMap<>();
    /** thread pool size */
    private final int threadPoolSize;
    /** scheduler thread pool */
    private ScheduledExecutorService executor;

    /**
     * Create a new GuiceViewManager with default thread pool size (2).
     */
    public GuiceViewManager() {
        this(DEFAULT_THREAD_POOL_SIZE, null, null);
    }

    /**
     * Create a new GuiceViewManager with specified thread pool size.
     *
     * @param threadPoolSize thread pool size for the scheduler
     * @throws IllegalArgumentException if threadPoolSize is less than 1
     */
    @Inject
    public GuiceViewManager(@Named(GuiceConstants.VIEW_MANAGER_THREAD_POOL_SIZE_KEY) int threadPoolSize) {
        this(threadPoolSize, null, null);
    }

    /**
     * Create a new GuiceViewManager with specified thread pool size and position repository.
     *
     * @param threadPoolSize    thread pool size for the scheduler
     * @param positionRepository projection position repository; null disables real status queries
     * @throws IllegalArgumentException if threadPoolSize is less than 1
     */
    public GuiceViewManager(int threadPoolSize, ProjectionPositionRepository positionRepository) {
        this(threadPoolSize, positionRepository, null);
    }

    /**
     * Create a new GuiceViewManager with specified thread pool size, position repository, and projection metrics.
     *
     * @param threadPoolSize    thread pool size for the scheduler
     * @param positionRepository projection position repository; null disables real status queries
     * @param projectionMetrics  projection metrics; null disables runtime status backfill
     * @throws IllegalArgumentException if threadPoolSize is less than 1
     */
    public GuiceViewManager(int threadPoolSize, ProjectionPositionRepository positionRepository,
                            ProjectionMetrics projectionMetrics) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Thread pool size must be at least 1: " + threadPoolSize);
        }
        this.threadPoolSize = threadPoolSize;
        this.positionRepository = positionRepository;
        this.projectionMetrics = projectionMetrics;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newScheduledThreadPool(threadPoolSize, runnable -> {
                Thread thread = new Thread(runnable, "ddd4j-runtime-guice-view-manager");
                thread.setDaemon(true);
                return thread;
            });
            log.info("GuiceViewManager started");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (Objects.nonNull(executor)) {
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("ViewManager executor did not terminate in time");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                executor = null;
            }
            handles.clear();
            log.info("GuiceViewManager stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void triggerOnce() {
        log.info("triggerOnce() should be implemented by concrete view logic");
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
                            info.getLastRunAt(), info.getLastEventCount(), info.getLastError()))
                    .orElse(new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null));
        }
        return new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null);
    }

    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        ensureStarted();
        long period = parseCronToPeriodSeconds(cron);
        return scheduleAtFixedRate(viewName, period, task);
    }

    /**
     * Schedule a task with a fixed interval (not depending on cron expression).
     *
     * @param viewName        view name (for logging / debugging)
     * @param intervalSeconds interval in seconds
     * @param task            task to execute
     * @return task handle that can be used to cancel
     * @throws IllegalArgumentException if intervalSeconds is less than or equal to 0
     */
    public ViewScheduleHandle scheduleAtFixedRate(String viewName, long intervalSeconds, Runnable task) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("Interval seconds must be positive: " + intervalSeconds);
        }
        ensureStarted();
        ScheduledFuture<?> future =
                executor.scheduleAtFixedRate(task, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        handles.put(viewName, future);
        log.info("View scheduled: {} period={}s", viewName, intervalSeconds);
        return new GuiceViewScheduleHandle(future);
    }

    private void ensureStarted() {
        if (!isRunning()) {
            start();
        }
    }

    // Parse cron expression to period seconds.
    // Supported formats: 0/N, */N (every N seconds), N * * * * (every N minutes), * * * * * (every 60 seconds)
    private long parseCronToPeriodSeconds(String cron) {
        if (StrKit.isEmpty(cron)) {
            throw new IllegalArgumentException("Cron expression must not be empty");
        }

        // try to match 0/N or */N format (every N seconds)
        if (cron.startsWith("0/") || cron.startsWith("*/")) {
            String intervalStr = cron.substring(2).split("\\s+")[0];
            try {
                long interval = Long.parseLong(intervalStr);
                if (interval <= 0) {
                    throw new IllegalArgumentException("Cron interval must be positive: " + cron);
                }
                return interval;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid cron expression: " + cron, exception);
            }
        }

        // try to match N * * * * format (every N minutes)
        String[] parts = cron.trim().split("\\s+");
        if (parts.length == 5 && parts[1].equals("*") && parts[2].equals("*") && parts[3].equals("*") && parts[4].equals("*")) {
            if (parts[0].equals("*")) {
                return 60L; // every minute
            }
            try {
                long minutes = Long.parseLong(parts[0]);
                if (minutes <= 0) {
                    throw new IllegalArgumentException("Cron interval must be positive: " + cron);
                }
                return minutes * 60;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid cron expression: " + cron, exception);
            }
        }

        throw new IllegalArgumentException("Unsupported cron expression format: " + cron
                + ". Supported formats: '0/N', '*/N', 'N * * * *', '* * * * *'");
    }

    private static class GuiceViewScheduleHandle implements ViewScheduleHandle {

        private final ScheduledFuture<?> future;

        GuiceViewScheduleHandle(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void cancel() {
            future.cancel(false);
        }

        @Override
        public boolean isActive() {
            return !future.isCancelled() && !future.isDone();
        }
    }
}
