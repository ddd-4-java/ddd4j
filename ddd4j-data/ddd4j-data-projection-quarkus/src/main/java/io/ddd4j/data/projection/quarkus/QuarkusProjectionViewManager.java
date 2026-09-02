package io.ddd4j.data.projection.quarkus;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus CDI 投影视图管理器（{@link ViewManager} SPI 的 Quarkus 实现）。
 *
 * <p>以 {@code @ApplicationScoped} 实现：ArC {@link Instance} 收集所有
 * {@link ProjectionView} Beans + {@link ProjectionRunner}；
 * {@link Startup} 事件触发 {@link #start()}（遍历 views 按 cron 调
 * {@link ViewScheduler#schedule}），{@link Shutdown} 事件触发 {@link #stop()}
 * （取消全部 handle），{@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see ProjectionRunner
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusProjectionViewManager implements ViewManager {

    private final ViewScheduler scheduler;

    private final ProjectionRunner<?> runner;

    private final Instance<ProjectionView<?>> views;

    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    @Inject
    public QuarkusProjectionViewManager(ViewScheduler scheduler,
                                         ProjectionRunner<?> runner,
                                         Instance<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 无参构造（<b>仅供 ArC 代理机制使用，业务代码不得调用</b>）：
     * {@code @ApplicationScoped} 客户端代理在构建期生成的子类需要一个可调用的
     * 非私有无参构造。
     */
    protected QuarkusProjectionViewManager() {
        this.scheduler = null;
        this.runner = null;
        this.views = null;
    }

    void onStart(@Observes Startup event) {
        start();
    }

    void onStop(@Observes Shutdown event) {
        stop();
    }

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
