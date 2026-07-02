package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.projection.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.concurrent.ScheduledFuture;

/**
 * Spring CRON 调度器（{@link ViewScheduler} SPI 的 Spring 实现）。
 *
 * <p>封装 {@link TaskScheduler}，提供基于 CRON 表达式的定时任务注册能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-SPRING : ViewScheduler ###")
public class SpringViewScheduler implements ViewScheduler {

    private final TaskScheduler taskScheduler;

    public SpringViewScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cron));
        log.info("View scheduled: {} cron={}", viewName, cron);
        return new SpringViewScheduleHandle(future);
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
