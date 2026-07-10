package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring CQRS 读侧视图管理器（基于 {@link TaskScheduler}）。
 *
 * <p>实现 ddd4j-core 的 {@link ViewManager} SPI，封装 Spring 的
 * {@code ScheduledTaskRegistrar}，按 CRON 表达式定时触发视图增量拉取。
 *
 * <p>由 {@code ddd4j-boot-ddd-autoconfigure} 注册为 Spring Bean，业务项目无需关心。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-SPRING : ViewManager ###")
public class SpringJpaViewManager implements ViewManager {

    /**
     * 视图调度器
     */
    private final ViewScheduler scheduler;
    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 已调度的视图任务句柄集
     */
    private final ConcurrentMap<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    public SpringJpaViewManager(ViewScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("SpringJpaViewManager started");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            handles.values().forEach(ViewScheduler.ViewScheduleHandle::cancel);
            handles.clear();
            log.info("SpringJpaViewManager stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void triggerOnce() {
        // 业务方可在测试或运维场景调用，由具体 View 实现增量拉取
        log.info("triggerOnce() - 业务方应在子类 override");
    }

    /**
     * 注册一个 CRON 调度的视图拉取任务。
     *
     * <p>供 {@code SpringEventHandlerRegistry} 在发现 View Bean 时调用：
     * <pre>{@code
     * springJpaViewManager.schedule("order-list-view", "0/5 * * * * ?", () -> view.update());
     * }</pre>
     */
    public ViewScheduler.ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(viewName, cron, task);
        handles.put(viewName, handle);
        return handle;
    }

    /**
     * Spring 上下文刷新完成后自动启动。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        start();
    }
}
