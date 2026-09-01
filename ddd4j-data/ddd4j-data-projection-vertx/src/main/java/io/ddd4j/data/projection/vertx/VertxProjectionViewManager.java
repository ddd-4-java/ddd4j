package io.ddd4j.data.projection.vertx;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vert.x 5 投影视图管理器（{@link ViewManager} SPI 的 Vert.x 实现）。
 *
 * <p>Vert.x 无 DI 容器——以静态 {@link #create} 工厂装配（与
 * {@code VertxCommandBus} 同款哲学），无 {@code @Inject} / {@code @PostConstruct}
 * 注解。{@link #create} 工厂内创建 {@link VertxProjectionScheduler}，
 * {@link #start()} 遍历 views 按 cron 调 {@link ViewScheduler#schedule}，
 * {@link #stop()} 取消全部 handle，{@link #triggerOnce()} 立即执行一次所有 view 的
 * runOnce。
 *
 * <p><b>Vert.x 模式</b>：不实现 {@code SmartLifecycle}（Vert.x 非 Spring）——
 * 集成方需在应用启动时手动调用 {@link #start()}，或在 Verticle {@code start()} 回调
 * 内触发。生命周期由 Vert.x {@code DeploymentOptions} 的 {@code instance()} 触发
 * 或集成方手动调。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see VertxProjectionScheduler
 * @see ProjectionRunner
 * @since 2.0.x
 */
public class VertxProjectionViewManager implements ViewManager {

    private final ViewScheduler scheduler;

    private final ProjectionRunner<?> runner;

    private final Collection<ProjectionView<?>> views;

    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private VertxProjectionViewManager(ViewScheduler scheduler,
                                        ProjectionRunner<?> runner,
                                        Collection<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 静态装配工厂（集成方一行入口）：创建视图管理器及其内部调度器。
     *
     * <p>须在 Vert.x 应用装配期调用——{@code vertx} 参数即装配锚点（强制视图管理器
     * 在真实 Vert.x 实例的存在下创建，拒绝脱离运行时的空中装配）。工厂内创建
     * {@link VertxProjectionScheduler}。
     *
     * @param vertx  集成方 Vert.x 实例（装配锚点，经 {@code Vertx.vertx()} 获取），非空
     * @param views  投影视图集合（由集成方经 ServiceLoader / 手动扫描供入），非空
     * @param runner 投影运行器（驱动 view 的 runOnce），非空
     * @return 已完成装配的视图管理器
     */
    public static VertxProjectionViewManager create(Vertx vertx,
                                                     Collection<ProjectionView<?>> views,
                                                     ProjectionRunner<?> runner) {
        Objects.requireNonNull(vertx, "vertx must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        VertxProjectionScheduler scheduler = VertxProjectionScheduler.create(vertx, views, runner);
        return new VertxProjectionViewManager(scheduler, runner, views);
    }

    /**
     * 启动视图管理器（注册所有 View + 启动调度）。
     *
     * <p><b>Vert.x 模式</b>：无 {@code @PostConstruct} 自动触发——集成方需在
     * 应用启动时手动调用本方法。典型调用点：
     * <ul>
     *   <li>Verticle {@code start()} 回调内 {@code viewManager.start()}</li>
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
