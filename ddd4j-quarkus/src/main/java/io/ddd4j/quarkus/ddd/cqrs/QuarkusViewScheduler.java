package io.ddd4j.quarkus.ddd.cqrs;

import io.ddd4j.core.cqrs.projection.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Quarkus CQRS 视图调度器。
 */
@Slf4j
@ApplicationScoped
public class QuarkusViewScheduler implements ViewScheduler {

    static final String TASK_KEY = "ddd4j.task";

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
