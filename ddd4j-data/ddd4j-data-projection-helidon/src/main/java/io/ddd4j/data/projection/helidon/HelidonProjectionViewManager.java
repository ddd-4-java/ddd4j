package io.ddd4j.data.projection.helidon;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helidon SE 投影视图管理器（{@link ViewManager} SPI 的 Helidon 实现）。
 *
 * <p>以 {@code @Singleton}（jakarta.inject）实现：构造器注入
 * {@link Collection}{@code <ProjectionView<?>>}（集成方通过 ServiceLoader 风格
 * 发现 {@code META-INF/services/io.ddd4j.core.cqrs.readmodel.ProjectionView} 注册
 * 全部 view 实现）+ {@link ProjectionRunner}；
 * {@link #start()} 遍历 views 按 cron 调 {@link ViewScheduler#schedule}，
 * {@link #stop()} 取消全部 handle，{@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * <p><b>Helidon SE 模式</b>：无 {@code @PostConstruct} 自动启动——集成方需在应用启动时
 * 手动调用 {@link #start()}（或注册 Helidon {@code io.helidon.common.context.Context}
 * lifecycle hook 触发）。这与 Quarkus 的 {@code @Observes Startup} /
 * Micronaut 的 {@code @PostConstruct} 自动启动不同，是 Helidon SE 的显式控制语义。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see ProjectionRunner
 * @since 2.0.x
 */
@Singleton
public class HelidonProjectionViewManager implements ViewManager {

    private final ViewScheduler scheduler;

    private final ProjectionRunner<?> runner;

    private final Collection<ProjectionView<?>> views;

    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    /**
     * 构造器注入（Helidon SE 的 jakarta.inject 注入语义）。
     *
     * @param scheduler 投影调度器（由集成方注册为服务或直接构造），非空
     * @param runner    投影运行器（由集成方注册为服务或直接构造），非空
     * @param views     投影视图集合（由集成方通过 ServiceLoader 发现或直接传入），非空
     */
    @Inject
    public HelidonProjectionViewManager(ViewScheduler scheduler,
                                         ProjectionRunner<?> runner,
                                         Collection<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 启动视图管理器（注册所有 View + 启动调度）。
     *
     * <p><b>Helidon SE 模式</b>：无 {@code @PostConstruct} 自动触发——集成方需在
     * 应用启动时手动调用本方法。典型调用点：
     * <ul>
     *   <li>{@code main()} 方法内 {@code viewManager.start()}</li>
     *   <li>Helidon {@code io.helidon.common.context.Context} lifecycle hook</li>
     *   <li>集成方自定义的启动钩子</li>
     * </ul>
     */
    @Override
    public void start() {
        if (!running && scheduler != null && views != null) {
            running = true;
            for (ProjectionView<?> view : views) {
                ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                        view.getName(), view.getCron(), () -> runView(view));
                handles.put(view.getName(), handle);
            }
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            handles.values().forEach(ViewScheduler.ViewScheduleHandle::cancel);
            handles.clear();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void triggerOnce() {
        if (views != null) {
            for (ProjectionView<?> view : views) {
                ((ProjectionRunner<Object>) runner).runOnce((ProjectionView<Object>) view);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void runView(ProjectionView<?> view) {
        ((ProjectionRunner<Object>) runner).runOnce((ProjectionView<Object>) view);
    }
}
