/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

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

    /**
     * Quartz JobDataMap 中任务标识的键名
     */
    static final String TASK_KEY = "ddd4j.task";

    /**
     * Quartz 调度器
     */
    @Inject
    Instance<Scheduler> schedulers;

    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        Scheduler scheduler = scheduler();
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

    private Scheduler scheduler() {
        if (schedulers.isUnsatisfied()) {
            throw new IllegalStateException("Quartz Scheduler is unavailable; add the quarkus-quartz extension");
        }
        if (schedulers.isAmbiguous()) {
            throw new IllegalStateException("Multiple Quartz Scheduler beans are available");
        }
        return schedulers.get();
    }
}
