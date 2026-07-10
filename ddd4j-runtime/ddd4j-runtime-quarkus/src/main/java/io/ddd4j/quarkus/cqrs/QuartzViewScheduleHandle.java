package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quartz 调度句柄。
 * <p>
 * {@link ViewScheduler.ViewScheduleHandle} 的 Quartz 实现，
 * 持有 {@link Scheduler} 引用以支持取消调度操作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
class QuartzViewScheduleHandle implements ViewScheduler.ViewScheduleHandle {

    /**
     * Quartz 调度器
     */
    private final Scheduler scheduler;
    /**
     * 调度标识
     */
    private final String identity;
    /**
     * 活跃状态标志
     */
    private final AtomicBoolean active = new AtomicBoolean(true);

    QuartzViewScheduleHandle(Scheduler scheduler, String identity) {
        this.scheduler = scheduler;
        this.identity = identity;
    }

    @Override
    public void cancel() {
        if (active.compareAndSet(true, false)) {
            try {
                scheduler.deleteJob(new JobKey(identity + "-job"));
            } catch (SchedulerException ex) {
                log.warn("Failed to cancel scheduled view job '{}'", identity, ex);
            } finally {
                RunnableHolder.remove(identity);
            }
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }
}
