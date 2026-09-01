package io.ddd4j.data.projection.micronaut;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.micronaut.context.BeanContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Micronaut CDI 投影视图管理器（{@link ViewManager} SPI 的 Micronaut 实现）。
 *
 * <p>以 {@code @Singleton} 实现：{@link BeanContext#getBeansOfType(Class)} 收集所有
 * {@link ProjectionView} Beans + {@link ProjectionRunner}；
 * {@link PostConstruct} 触发 {@link #start()}（遍历 views 按 cron 调
 * {@link ViewScheduler#schedule}），{@link PreDestroy} 触发 {@link #stop()}
 * （取消全部 handle），{@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see ProjectionRunner
 * @since 2.0.x
 */
@Singleton
public class MicronautProjectionViewManager implements ViewManager {

    private final ViewScheduler scheduler;

    private final ProjectionRunner<?> runner;

    private final Collection<ProjectionView<?>> views;

    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    @Inject
    @SuppressWarnings("unchecked")
    public MicronautProjectionViewManager(ViewScheduler scheduler,
                                           ProjectionRunner<?> runner,
                                           BeanContext context) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = (Collection<ProjectionView<?>>) (Collection<?>) context.getBeansOfType(ProjectionView.class);
    }

    /**
     * 无参构造（<b>仅供 Micronaut AOP 代理机制使用，业务代码不得调用</b>）：
     * {@code @Singleton} 客户端代理在构建期生成的子类需要一个可调用的
     * 非私有无参构造。
     */
    protected MicronautProjectionViewManager() {
        this.scheduler = null;
        this.runner = null;
        this.views = null;
    }

    @PostConstruct
    void onPostConstruct() {
        start();
    }

    @PreDestroy
    void onPreDestroy() {
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
