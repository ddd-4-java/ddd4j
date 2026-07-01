package io.ddd4j.quarkus.cqrs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.util.Objects;

/**
 * Quartz Job 适配。
 */
public class RunnableJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        String identity = context.getJobDetail().getJobDataMap().getString(QuarkusViewScheduler.TASK_KEY);
        Runnable task = RunnableHolder.get(identity);
        if (Objects.nonNull(task)) {
            task.run();
        }
    }
}
