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

import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
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

    /** \u9009\u62e9 Quarkus \u6295\u5f71\u8fd0\u884c\u6a21\u5f0f\u7684\u914d\u7f6e\u9879\u3002 */
    static final String MODE_KEY = "ddd4j.quarkus.cqrs.projection.mode";
    /** \u8bfb\u53d6 Quarkus \u6295\u5f71\u8c03\u5ea6 Cron \u8868\u8fbe\u5f0f\u7684\u914d\u7f6e\u9879\u3002 */
    static final String CRON_KEY = "ddd4j.quarkus.cqrs.projection.cron";
    /** \u914d\u7f6e\u672a\u7ed9\u5b9a\u65f6\u91c7\u7528\u7684\u9ed8\u8ba4\u6295\u5f71 Cron \u8868\u8fbe\u5f0f\u3002 */
    static final String DEFAULT_CRON = "*/30 * * * * ?";
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
    /** \u7528\u4e8e\u8bfb\u53d6\u6295\u5f71\u8fd0\u884c\u6a21\u5f0f\u548c\u8c03\u5ea6\u914d\u7f6e\u7684 MicroProfile \u914d\u7f6e\u3002 */
    @Inject
    Config config;
    /** \u517c\u5bb9\u8c03\u5ea6\u8def\u5f84\u53ef\u4f7f\u7528\u7684\u6295\u5f71\u670d\u52a1\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<QuarkusJpaProjectionService> legacyServices;
    /** CDI \u5bb9\u5668\u63d0\u4f9b\u7684\u6295\u5f71\u4e8b\u4ef6\u8fd0\u884c\u5668\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<ProjectionRunner<Object>> runners;
    /** \u7531\u5f53\u524d\u8fd0\u884c\u73af\u5883\u7ba1\u7406\u548c\u8c03\u5ea6\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    @Inject
    Instance<ProjectionView<Object>> views;
    /** CDI \u5bb9\u5668\u63d0\u4f9b\u7684\u8c03\u5ea6\u5668\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<ViewScheduler> schedulers;
    /** CDI \u5bb9\u5668\u63d0\u4f9b\u7684\u89c6\u56fe\u7ba1\u7406\u5668\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<ViewManager> managers;
    /** CDI \u5bb9\u5668\u63d0\u4f9b\u7684\u4e8b\u4ef6\u8bfb\u53d6\u5668\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<EventChunkReader<Object>> readers;
    /** CDI \u5bb9\u5668\u63d0\u4f9b\u7684\u4e8b\u52a1\u7ba1\u7406\u5668\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<TransactionManager> transactionManagers;
    /** \u91c7\u7528\u663e\u5f0f\u6295\u5f71\u89c6\u56fe\u914d\u7f6e\u65f6\u4f7f\u7528\u7684\u7ba1\u7406\u5668\u3002 */
    private DefaultViewManager<Object> delegate;
    /** \u7528\u4e8e\u53d6\u6d88\u517c\u5bb9\u6295\u5f71\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4\u3002 */
    private ViewScheduler.ViewScheduleHandle legacyHandle;
    /** \u5f53\u524d\u517c\u5bb9\u8c03\u5ea6\u8def\u5f84\u5b9e\u9645\u4f7f\u7528\u7684\u6295\u5f71\u670d\u52a1\u3002 */
    private QuarkusJpaProjectionService legacyService;

    /**
     * \u4ece CDI \u5019\u9009\u4e2d\u89e3\u6790\u552f\u4e00\u7684\u6295\u5f71\u7ec4\u4ef6\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param candidates CDI \u5bb9\u5668\u53ef\u63d0\u4f9b\u7684\u5019\u9009\u7ec4\u4ef6
     * @param name \u5f53\u524d\u7ec4\u4ef6\u3001\u6307\u6807\u3001\u4efb\u52a1\u6216 Bean \u4f7f\u7528\u7684\u540d\u79f0
     * @return \u627e\u5230\u7684\u552f\u4e00\u5019\u9009\uff0c\u672a\u914d\u7f6e\u65f6\u4e3a\u7a7a
     */
    private static <T> T unique(Instance<T> candidates, String name) {
        if (!candidates.isResolvable()) {
            throw new IllegalStateException("A unique " + name + " is required for projection");
        }
        return candidates.get();
    }

    /**
     * \u5728 Quarkus \u542f\u52a8\u540e\u9009\u62e9\u5e76\u542f\u52a8\u6295\u5f71\u8fd0\u884c\u6a21\u5f0f\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
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
    /**
     * \u6309\u663e\u5f0f\u6216\u517c\u5bb9\u6295\u5f71\u914d\u7f6e\u88c5\u914d\u8c03\u5ea6\u4efb\u52a1\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void launch(@Observes @jakarta.annotation.Priority(4000) StartupEvent event) {
        if (Objects.nonNull(delegate) || Objects.nonNull(legacyService)) {
            start();
        }
    }

    /**
     * \u5728\u505c\u6b62\u4e8b\u4ef6\u4e2d\u6e05\u7406\u5f53\u524d\u7ba1\u7406\u5668\u542f\u52a8\u7684\u6295\u5f71\u4efb\u52a1\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void onStop(@Observes ShutdownEvent event) {
        stop();
    }

    /** \u5173\u95ed\u7531\u672c\u7ba1\u7406\u5668\u62e5\u6709\u7684\u663e\u5f0f\u6216\u517c\u5bb9\u6295\u5f71\u89c6\u56fe\u3002 */
    @jakarta.annotation.PreDestroy
    void destroy() {
        stop();
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u7ba1\u7406\u5668\u662f\u5426\u8d1f\u8d23\u7ba1\u7406\u6295\u5f71\u8fd0\u884c\u72b6\u6001\u3002
     * @return \u5f53\u524d\u5b9e\u4f8b\u62e5\u6709\u6295\u5f71\u542f\u505c\u804c\u8d23\u65f6\u8fd4\u56de true
     */
    private boolean ownsLifecycle() {
        return ClientProxy.unwrap(unique(managers, "ViewManager")) == this;
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>JPA 实现调用 <code>running.get()</code>。</p>
     */
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
    /** \u5728\u5df2\u6fc0\u6d3b\u7684 CDI \u8bf7\u6c42\u4e0a\u4e0b\u6587\u4e2d\u6267\u884c\u517c\u5bb9\u6295\u5f71\u4efb\u52a1\u3002 */
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

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>JPA 实现调用 <code>running.set(false)</code>。</p>
     */
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
     * <p>JPA 实现调用 <code>delegate.triggerOnce()</code>。</p>
     */
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
