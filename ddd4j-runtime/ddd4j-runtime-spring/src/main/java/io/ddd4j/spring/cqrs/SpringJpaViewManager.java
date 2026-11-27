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

    /**
     * \u6784\u9020\u89c6\u56fe\u7ba1\u7406\u5668\uff08\u65e0\u4f4d\u7f6e\u4ed3\u50a8\uff0cgetProjectionStatus \u8fd4\u56de\u57fa\u7ebf\u72b6\u6001\uff09\u3002
     * @param scheduler \u89c6\u56fe\u8c03\u5ea6\u5668
     */
    public SpringJpaViewManager(ViewScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>JPA 实现调用 <code>running.compareAndSet(false, true)</code>。</p>
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("SpringJpaViewManager started");
        }
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>JPA 实现调用 <code>running.compareAndSet(true, false)</code>。</p>
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            handles.values().forEach(ViewScheduler.ViewScheduleHandle::cancel);
            handles.clear();
            log.info("SpringJpaViewManager stopped");
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
     * <p>JPA 实现调用 <code>log.info("triggerOnce() - \u4e1a\u52a1\u65b9\u5e94\u5728\u5b50\u7c7b override")</code>。</p>
     */
    @Override
    public void triggerOnce() {
        // 业务方可在测试或运维场景调用，由具体 View 实现增量拉取
        log.info("triggerOnce() - 业务方应在子类 override");
    }

    /**
     * \u6ce8\u518c\u4e00\u4e2a CRON \u8c03\u5ea6\u7684\u89c6\u56fe\u62c9\u53d6\u4efb\u52a1\u3002
     *
     *  <p>\u4f9b {@code SpringEventHandlerRegistry} \u5728\u53d1\u73b0 View Bean \u65f6\u8c03\u7528\uff1a
     *  <pre>{@code
     *  springJpaViewManager.schedule("order-list-view", "0/5 * * * * ?", () -> view.update());
     *  }</pre>
     *
     * @param viewName \u89c6\u56fe\u540d\u79f0
     * @param cron \u8c03\u5ea6\u8868\u8fbe\u5f0f
     * @param task \u66f4\u65b0\u89c6\u56fe\u7684\u4efb\u52a1
     * @return \u53ef\u53d6\u6d88\u7684\u8c03\u5ea6\u53e5\u67c4
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
