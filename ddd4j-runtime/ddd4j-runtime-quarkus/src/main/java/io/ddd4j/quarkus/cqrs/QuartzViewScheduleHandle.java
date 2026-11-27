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
     * cancel() 方法。
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
     * 判断布尔状态。
     * @return 判断结果。
     */
    @Override
    public boolean isActive() {
        return active.get();
    }
}
