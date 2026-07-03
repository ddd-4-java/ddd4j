package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;

import java.util.UUID;

/**
 * Quarkus CQRS 视图调度器。
 * <p>
 * 基于 Quartz {@link Scheduler} 实现 {@link ViewScheduler} SPI，
 * 按 CRON 表达式定时触发视图增量拉取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class QuarkusViewScheduler implements ViewScheduler {

    /** Quartz JobDataMap 中任务标识的键名 */
    static final String TASK_KEY = "ddd4j.task";

    /** Quartz 调度器 */
    @Inject
    Scheduler scheduler;

    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        String identity = viewName + "-" + UUID.randomUUID();
        try {
            JobDetail job = JobBuilder.newJob(RunnableJob.class)
                    .withIdentity(identity + "-job")
                    .usingJobData(TASK_KEY, identity)
                    .storeDurably()
                    .build();
            RunnableHolder.put(identity, task);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(identity + "-trigger")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .build();

            scheduler.scheduleJob(job, trigger);
            log.info("Scheduled view '{}' with cron '{}'", viewName, cron);
            return new QuartzViewScheduleHandle(scheduler, identity);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to schedule view '" + viewName + "' with cron '" + cron + "'", ex);
        }
    }
}
