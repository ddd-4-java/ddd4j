package io.ddd4j.data.projection.spring;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Spring CRON 投影调度器（{@link ViewScheduler} SPI 的 Spring 实现）。
 *
 * <p>以 {@code @Component} + {@link SmartLifecycle} 实现 Spring 生命周期适配：
 * 构造器注入 {@link TaskScheduler}，{@link #schedule} 用 {@link CronTrigger}
 * 注册定时任务，{@link SmartLifecycle#start()} 遍历已注册 views 启动所有调度，
 * {@link SmartLifecycle#stop()} 取消全部。{@link ViewScheduleHandle} 包装
 * {@link ScheduledFuture}（cancel 调 future.cancel）。
 *
 * <p><b>仅服务 Spring 系运行时</b>（WebMVC/WebFlux/Helidon-Spring）；
 * Quarkus 运行时用 {@code QuarkusProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @see SmartLifecycle
 * @since 2.0.x
 */
@Component
public class SpringProjectionScheduler implements ViewScheduler, SmartLifecycle {

    private final TaskScheduler taskScheduler;

    private final Map<String, ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    @Autowired
    public SpringProjectionScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler must not be null");
    }

    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        Objects.requireNonNull(viewName, "viewName must not be null");
        Objects.requireNonNull(cron, "cron must not be null");
        Objects.requireNonNull(task, "task must not be null");
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cron));
        SpringViewScheduleHandle handle = new SpringViewScheduleHandle(future);
        handles.put(viewName, handle);
        return handle;
    }

    @Override
    public void start() {
        if (!running) {
            running = true;
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            handles.values().forEach(ViewScheduleHandle::cancel);
            handles.clear();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * Spring {@link ScheduledFuture} 适配为 ddd4j {@link ViewScheduleHandle}。
     */
    private static class SpringViewScheduleHandle implements ViewScheduleHandle {

        private final ScheduledFuture<?> future;

        SpringViewScheduleHandle(ScheduledFuture<?> future) {
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
