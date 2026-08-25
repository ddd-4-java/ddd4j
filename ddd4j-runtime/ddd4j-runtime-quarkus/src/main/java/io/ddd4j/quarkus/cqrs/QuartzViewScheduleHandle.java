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

    QuartzViewScheduleHandle(Scheduler scheduler, String identity) {
        this.scheduler = scheduler;
        this.identity = identity;
    }

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

    @Override
    public boolean isActive() {
        return active.get();
    }
}
