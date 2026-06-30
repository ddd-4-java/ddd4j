package io.ddd4j.quarkus.ddd.cqrs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * Quartz Job 适配。
 */
public class RunnableJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        String identity = context.getJobDetail().getJobDataMap().getString(QuarkusViewScheduler.TASK_KEY);
        Runnable task = RunnableHolder.get(identity);
        if (task != null) {
            task.run();
        }
    }
}
