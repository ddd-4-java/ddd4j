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
     * 用给定值初始化 <code>QuartzViewScheduleHandle</code> 实例。
     *
     * @param scheduler Quartz 调度器
     * @param identity  调度标识
     */
    QuartzViewScheduleHandle(Scheduler scheduler, String identity) {
        this.scheduler = scheduler;
        this.identity = identity;
    }


    /**
     * 根据视图名称和 Cron 配置注册一次持续有效的 Quartz 投影计划。
     *
     * @param scheduler 调度投影任务的运行时组件
     * @param viewName  当前被调度的视图名称
     * @param cron      任务计划的 Cron 表达式
     * @param task      实际执行一次投影的回调
     * @return 可取消该计划的调度句柄
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
