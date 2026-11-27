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

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>QuartzViewScheduleHandle</code> \u5b9e\u4f8b\u3002
     * @param scheduler Quartz \u8c03\u5ea6\u5668
     * @param identity \u8c03\u5ea6\u6807\u8bc6
     */
    QuartzViewScheduleHandle(Scheduler scheduler, String identity) {
        this.scheduler = scheduler;
        this.identity = identity;
    }

    /**
     * 撤销当前任务句柄允许撤销的操作。
     *
     * <p>此实现调用 <code>active.compareAndSet(true, false)</code>。</p>
     */
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

    /**
     * 检查当前句柄是否仍处于有效状态。
     *
     * <p>此实现调用 <code>active.get()</code>。</p>
     *
     * @return 实际返回<code>active.get()</code>
     */
    @Override
    public boolean isActive() {
        return active.get();
    }
}
