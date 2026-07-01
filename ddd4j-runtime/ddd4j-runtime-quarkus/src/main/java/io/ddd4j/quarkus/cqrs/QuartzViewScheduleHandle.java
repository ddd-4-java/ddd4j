package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.projection.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quartz 调度句柄。
 */
@Slf4j
class QuartzViewScheduleHandle implements ViewScheduler.ViewScheduleHandle {

    private final Scheduler scheduler;
    private final String identity;
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
