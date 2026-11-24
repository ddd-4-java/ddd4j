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

import io.ddd4j.core.cqrs.readmodel.DefaultViewManager;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.NoopEventChunkReader;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.common.runtime.util.SchedulerUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quarkus CQRS 原生生命周期与可选能力装配。
 *
 * <p>实现 ddd4j-core 的 {@link ViewManager} SPI。Quarkus 通过 CDI 事件
 * （{@link StartupEvent} / {@link ShutdownEvent}）自动触发 start/stop。
 *
 * <p>durable 模式委托共享视图管理器；旧业务子类或原 cron 配置启用旧拉取钩子。
 * 基础模式不创建投影任务，旧 GAV 的具体管理器通过原生 DefaultBean 规则优先。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-QUARKUS : ViewManager ###")
@ApplicationScoped
@DefaultBean
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

    static final String MODE_KEY = "ddd4j.quarkus.cqrs.projection.mode";
    static final String CRON_KEY = "ddd4j.quarkus.cqrs.projection.cron";
    static final String DEFAULT_CRON = "*/30 * * * * ?";

    @Inject Config config;
    @Inject Instance<QuarkusJpaProjectionService> legacyServices;
    @Inject Instance<ProjectionRunner<Object>> runners;
    @Inject Instance<ProjectionView<Object>> views;
    @Inject Instance<ViewScheduler> schedulers;
    @Inject Instance<ViewManager> managers;
    @Inject Instance<EventChunkReader<Object>> readers;
    @Inject Instance<TransactionManager> transactionManagers;
    private DefaultViewManager<Object> delegate;
    private ViewScheduler.ViewScheduleHandle legacyHandle;
    private QuarkusJpaProjectionService legacyService;

    synchronized void onStart(@Observes @jakarta.annotation.Priority(1000) StartupEvent event) {
        if (running.get()) {
            return;
        }
        String mode = config.getOptionalValue(MODE_KEY, String.class).orElse("off");
        if (!List.of("off", "legacy", "durable").contains(mode)) {
            throw new IllegalStateException("Unknown projection mode: " + mode);
        }
        boolean customLegacy = false;
        for (Instance.Handle<QuarkusJpaProjectionService> handle : legacyServices.handles()) {
            customLegacy |= handle.getBean().getBeanClass() != QuarkusJpaProjectionService.class;
        }
        if ("durable".equals(mode)) {
            if (customLegacy) {
                throw new IllegalStateException("Choose either durable views or the custom legacy projection service");
            }
            EventChunkReader<Object> reader = unique(readers, "EventChunkReader");
            if (reader instanceof NoopEventChunkReader<?>) {
                throw new IllegalStateException("Durable projection cannot use NoopEventChunkReader");
            }
            ProjectionPositionRepository repository = unique(positionRepositories, "ProjectionPositionRepository");
            if (repository instanceof InMemoryProjectionPositionRepository) {
                throw new IllegalStateException("Durable projection cannot use in-memory positions");
            }
            unique(transactionManagers, "JTA TransactionManager (quarkus-narayana-jta)");
            ProjectionRunner<Object> runner = unique(runners, "ProjectionRunner");
            if (!(runner instanceof QuarkusProjectionRunner)) {
                throw new IllegalStateException("Enable durable projection mode during Quarkus augmentation to install its JTA runner");
            }
            // @DefaultBean 被覆盖时其观察者仍会运行：保留能力校验，但仅由 CDI 实际选中的实例装配任务。
            if (ownsLifecycle()) {
                List<ProjectionView<Object>> snapshot = new ArrayList<>();
                views.forEach(snapshot::add);
                delegate = new DefaultViewManager<>(unique(schedulers, "ViewScheduler"), runner, snapshot);
            }
        } else if ("legacy".equals(mode) || customLegacy || config.getOptionalValue(CRON_KEY, String.class).isPresent()) {
            QuarkusJpaProjectionService service = unique(legacyServices, "QuarkusJpaProjectionService");
            unique(positionRepositories, "ProjectionPositionRepository");
            if (ownsLifecycle()) {
                legacyService = service;
            }
        }
    }

    // 先完成能力装配，再由默认优先级初始化器发布 SPI，最后才允许业务任务执行。
    void launch(@Observes @jakarta.annotation.Priority(4000) StartupEvent event) {
        if (Objects.nonNull(delegate) || Objects.nonNull(legacyService)) {
            start();
        }
    }

    void onStop(@Observes ShutdownEvent event) { stop(); }

    @jakarta.annotation.PreDestroy
    void destroy() { stop(); }

    private boolean ownsLifecycle() {
        return ClientProxy.unwrap(unique(managers, "ViewManager")) == this;
    }

    private static <T> T unique(Instance<T> candidates, String name) {
        if (!candidates.isResolvable()) {
            throw new IllegalStateException("A unique " + name + " is required for projection");
        }
        return candidates.get();
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        if (Objects.nonNull(delegate)) {
            delegate.start();
        } else if (Objects.nonNull(legacyService)) {
            // 与原 @Scheduled 共用平台表达式展开和 off/disabled 规则；不把 Quartz 方言交给数值解析器。
            String cron;
            try {
                cron = SchedulerUtils.lookUpPropertyValue(config.getOptionalValue(CRON_KEY, String.class)
                        .orElse(DEFAULT_CRON));
            } catch (NoClassDefFoundError missingQuartz) {
                throw new IllegalStateException("Legacy projection scheduling requires the quarkus-quartz extension", missingQuartz);
            }
            if (SchedulerUtils.isOff(cron) || !config.getOptionalValue("quarkus.scheduler.enabled", Boolean.class).orElse(true)) {
                return;
            }
            legacyHandle = unique(schedulers, "ViewScheduler").schedule("ddd4j-legacy-projection", cron,
                    this::runLegacyWithRequestContext);
        }
        running.set(true);
    }

    // 普通 Quartz Job 不经过 @Scheduled invoker，需在真实定时边界恢复其原请求作用域。
    private void runLegacyWithRequestContext() {
        var requestContext = Arc.container().requestContext();
        boolean owner = !requestContext.isActive();
        if (owner) {
            requestContext.activate();
        }
        try {
            legacyService.runOnce();
        } finally {
            if (owner) {
                requestContext.terminate();
            }
        }
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        if (Objects.nonNull(delegate)) {
            delegate.stop();
        }
        if (Objects.nonNull(legacyHandle)) {
            legacyHandle.cancel();
            legacyHandle = null;
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public void triggerOnce() {
        if (Objects.nonNull(delegate)) {
            delegate.triggerOnce();
        } else if (Objects.nonNull(legacyService)) {
            legacyService.runOnce();
        }
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
        if (Objects.nonNull(delegate)) {
            return delegate.getProjectionStatus(streamId);
        }
        if (Objects.isNull(positionRepositories) || positionRepositories.isUnsatisfied()) {
            return ProjectionStatus.baseline(streamId, isRunning());
        }
        ProjectionPositionRepository repository = positionRepositories.get();
        Optional<ProjectionPosition> position = repository.findByStreamId(streamId);
        long nextEventNumber = position.map(ProjectionPosition::getNextEventNumber).orElse(0L);
        if (Objects.nonNull(projectionMetricsInstances) && !projectionMetricsInstances.isUnsatisfied()) {
            ProjectionMetrics metrics = projectionMetricsInstances.get();
            return metrics.getLastRunInfo(streamId)
                    .map(info -> new ProjectionStatus(streamId, nextEventNumber, isRunning(),
                            info.lastRunAt(), info.lastEventCount(), info.lastError()))
                    .orElse(new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null));
        }
        return new ProjectionStatus(streamId, nextEventNumber, isRunning(), null, 0, null);
    }
}
