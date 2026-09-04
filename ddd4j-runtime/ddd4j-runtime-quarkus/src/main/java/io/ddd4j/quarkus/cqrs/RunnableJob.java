package io.ddd4j.quarkus.cqrs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.util.Objects;

/**
 * Quartz Job 适配。
 * <p>
 * 将 {@link Runnable} 任务包装为 Quartz {@link Job} 执行，
 * 通过 {@link RunnableHolder} 从 JobDataMap 中获取对应任务实例。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
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
