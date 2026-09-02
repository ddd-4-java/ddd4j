package io.ddd4j.data.projection.dropwizard;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.dropwizard.core.setup.Environment;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ViewManager} 的 Dropwizard 5 装配适配器：以静态
 * {@link #create(Environment, Collection, ProjectionRunner)} 工厂创建管理器实例。
 *
 * <p>{@link #start()} 遍历所有 views 按 cron 调
 * {@link ViewScheduler#schedule}，{@link #stop()} 取消全部 handle，
 * {@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * <p>生命周期由集成方在 {@code Application.run} 内手动调
 * {@code create} 后触发（manual-registration 模式，与 cqrs-dropwizard
 * 的 {@code create(Application, Collection)} 工厂对称）。
 *
 * <p><b>仅服务 Dropwizard 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionViewManager}，Quarkus 用
 * {@code QuarkusProjectionViewManager}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see DropwizardProjectionScheduler
 * @see ProjectionRunner
 * @since 2.0.x
 */
public class DropwizardProjectionViewManager implements ViewManager {

    private final ViewScheduler scheduler;

    private final ProjectionRunner<?> runner;

    private final Collection<ProjectionView<?>> views;

    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private DropwizardProjectionViewManager(ViewScheduler scheduler,
                                             ProjectionRunner<?> runner,
                                             Collection<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 静态装配工厂（集成方一行入口）：在 Dropwizard 应用装配期——集成方
     * {@code Application.run} 内（Jetty 启动之前）调用一次，创建管理器实例。
     *
     * @param env    Dropwizard 环境（装配锚点），非空
     * @param views  集成方供入的投影视图集合，非空
     * @param runner 投影运行器，非空
     * @return 管理器实例
     */
    public static DropwizardProjectionViewManager create(Environment env,
                                                          Collection<ProjectionView<?>> views,
                                                          ProjectionRunner<?> runner) {
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        DropwizardProjectionScheduler scheduler = DropwizardProjectionScheduler.create(env, views, runner);
        return new DropwizardProjectionViewManager(scheduler, runner, views);
    }

    @Override
    public void start() {
        if (!running) {
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
        ((ProjectionRunner<Object>) runner).runAll(
                (Collection<? extends ProjectionView<Object>>) (Collection<?>) views);
    }

    @SuppressWarnings("unchecked")
    private void runView(ProjectionView<?> view) {
        ((ProjectionRunner<Object>) runner).runOnce((ProjectionView<Object>) view);
    }
}
