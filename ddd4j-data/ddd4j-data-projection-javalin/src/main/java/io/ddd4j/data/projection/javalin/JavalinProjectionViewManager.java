package io.ddd4j.data.projection.javalin;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.javalin.Javalin;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Javalin 投影视图管理器（{@link ViewManager} SPI 的 Javalin 实现）。
 *
 * <p>Javalin 无 DI 容器——以静态 {@link #create} 工厂装配（与
 * {@code JavalinCommandBus} 同款哲学），无 {@code @Inject} / {@code @PostConstruct}
 * 注解。{@link #create} 工厂内创建 {@link JavalinProjectionScheduler}（经
 * {@code app.events(serverStopping)} 注册生命周期钩子），{@link #start()} 遍历
 * views 按 cron 调 {@link ViewScheduler#schedule}，{@link #stop()} 取消全部 handle，
 * {@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * <p><b>Javalin 模式</b>：不实现 {@code SmartLifecycle}（Javalin 非 Spring）——
 * 集成方需在应用启动时手动调用 {@link #start()}，或在 Javalin
 * {@code app.events(events -> events.serverStarted(...))} 钩子内触发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see JavalinProjectionScheduler
 * @see ProjectionRunner
 * @since 2.0.x
 */
public class JavalinProjectionViewManager implements ViewManager {

    private final ViewScheduler scheduler;

    private final ProjectionRunner<?> runner;

    private final Collection<ProjectionView<?>> views;

    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private JavalinProjectionViewManager(ViewScheduler scheduler,
                                          ProjectionRunner<?> runner,
                                          Collection<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 静态装配工厂（集成方一行入口）：创建视图管理器及其内部调度器。
     *
     * <p>须在 Javalin 应用装配期调用——{@code app} 参数即装配锚点（强制视图管理器
     * 在真实 Javalin 应用实例的存在下创建，拒绝脱离应用的空中装配）。工厂内创建
     * {@link JavalinProjectionScheduler} 并经
     * {@code app.events(events -> events.serverStopping(...))} 注册关闭钩子。
     *
     * @param app    集成方 Javalin 应用实例（装配锚点 + 生命周期钩子宿主），非空
     * @param views  投影视图集合（由集成方经 ServiceLoader / 手动扫描供入），非空
     * @param runner 投影运行器（驱动 view 的 runOnce），非空
     * @return 已完成装配的视图管理器
     */
    public static JavalinProjectionViewManager create(Javalin app,
                                                       Collection<ProjectionView<?>> views,
                                                       ProjectionRunner<?> runner) {
        Objects.requireNonNull(app, "app must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        JavalinProjectionScheduler scheduler = JavalinProjectionScheduler.create(app, views, runner);
        return new JavalinProjectionViewManager(scheduler, runner, views);
    }

    /**
     * 启动视图管理器（注册所有 View + 启动调度）。
     *
     * <p><b>Javalin 模式</b>：无 {@code @PostConstruct} 自动触发——集成方需在
     * 应用启动时手动调用本方法。典型调用点：
     * <ul>
     *   <li>{@code Javalin.create(...)} 装配期内 {@code viewManager.start()}</li>
     *   <li>Javalin {@code app.events(events -> events.serverStarted(...))} 钩子内</li>
     *   <li>集成方自定义的启动逻辑</li>
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
