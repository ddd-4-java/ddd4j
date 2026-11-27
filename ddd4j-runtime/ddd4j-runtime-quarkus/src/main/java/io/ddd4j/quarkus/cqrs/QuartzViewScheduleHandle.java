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
import org.quartz.*;

import java.util.UUID;

/**
 * Quartz 调度句柄。
 * <p>
 * {@link ViewScheduler.ViewScheduleHandle} 的 Quartz 实现，
 * 持有 {@link Scheduler} 引用以支持取消调度操作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
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
    private volatile boolean active = true;

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
     * \u6839\u636e\u89c6\u56fe\u540d\u79f0\u548c Cron \u914d\u7f6e\u6ce8\u518c\u4e00\u6b21\u6301\u7eed\u6709\u6548\u7684 Quartz \u6295\u5f71\u8ba1\u5212\u3002
     * @param scheduler \u8c03\u5ea6\u6295\u5f71\u4efb\u52a1\u7684\u8fd0\u884c\u65f6\u7ec4\u4ef6
     * @param viewName \u5f53\u524d\u88ab\u8c03\u5ea6\u7684\u89c6\u56fe\u540d\u79f0
     * @param cron \u4efb\u52a1\u8ba1\u5212\u7684 Cron \u8868\u8fbe\u5f0f
     * @param task \u5b9e\u9645\u6267\u884c\u4e00\u6b21\u6295\u5f71\u7684\u56de\u8c03
     * @return \u53ef\u53d6\u6d88\u8be5\u8ba1\u5212\u7684\u8c03\u5ea6\u53e5\u67c4
     */
    static QuartzViewScheduleHandle schedule(Scheduler scheduler, String viewName, String cron, Runnable task) {
        String identity = viewName + "-" + UUID.randomUUID();
        JobDetail job = JobBuilder.newJob(RunnableJob.class)
                .withIdentity(identity + "-job")
                .usingJobData(QuarkusViewScheduler.TASK_KEY, identity)
                .storeDurably().build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(identity + "-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build();
        RunnableHolder.put(identity, task);
        boolean scheduled = false;
        try {
            scheduler.scheduleJob(job, trigger);
            scheduled = true;
            return new QuartzViewScheduleHandle(scheduler, identity);
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Failed to schedule view '" + viewName + "' with cron '" + cron + "'", failure);
        } finally {
            if (!scheduled) {
                RunnableHolder.remove(identity);
            }
        }
    }

    /**
     * 撤销当前任务句柄允许撤销的操作。
     *
     * <p>此实现调用 <code>scheduler.deleteJob(new JobKey(identity + "-job"))</code>。</p>
     */
    @Override
    public synchronized void cancel() {
        if (!active) {
            return;
        }
        active = false;
        boolean deleted = false;
        try {
            scheduler.deleteJob(new JobKey(identity + "-job"));
            deleted = true;
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Failed to cancel scheduled view job '" + identity + "'", failure);
        } finally {
            active = !deleted;
            RunnableHolder.remove(identity);
        }
    }

    /**
     * 检查当前句柄是否仍处于有效状态。
     *
     * @return 实际返回<code>active</code>，作为本次判断结果
     */
    @Override
    public boolean isActive() {
        return active;
    }
}
