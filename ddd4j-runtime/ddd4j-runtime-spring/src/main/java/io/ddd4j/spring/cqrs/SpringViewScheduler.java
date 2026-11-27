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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.concurrent.ScheduledFuture;

/**
 * Spring CRON 调度器（{@link ViewScheduler} SPI 的 Spring 实现）。
 *
 * <p>封装 {@link TaskScheduler}，提供基于 CRON 表达式的定时任务注册能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-SPRING : ViewScheduler ###")
public class SpringViewScheduler implements ViewScheduler {

    /**
     * Spring 任务调度器
     */
    private final TaskScheduler taskScheduler;

    /**
     * 创建 Spring 任务调度适配器。
     *
     * @param taskScheduler Spring 任务调度器
     */
    public SpringViewScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Spring 实现调用 <code>taskScheduler.schedule(task, new CronTrigger(cron))</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron CRON 表达式
     * @param task 要执行的任务
     * @return 任务句柄，可用于取消
     */
    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cron));
        log.info("View scheduled: {} cron={}", viewName, cron);
        return new SpringViewScheduleHandle(future);
    }

    /**
     * Spring {@link ScheduledFuture} 适配为 ddd4j {@link ViewScheduleHandle}。
     */
    private static class SpringViewScheduleHandle implements ViewScheduleHandle {
        /** \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4\u3002 */
        private final ScheduledFuture<?> future;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>SpringViewScheduleHandle</code> \u5b9e\u4f8b\u3002
         * @param future \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4
         */
        SpringViewScheduleHandle(ScheduledFuture<?> future) {
            this.future = future;
        }

        /**
         * 撤销当前任务句柄允许撤销的操作。
         *
         * <p>Spring 实现调用 <code>future.cancel(false)</code>。</p>
         */
        @Override
        public void cancel() {
            future.cancel(false);
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * <p>Spring 实现调用 <code>future.isCancelled()</code>。</p>
         *
         * @return 实际返回<code>!future.isCancelled() &amp;&amp; !future.isDone()</code>
         */
        @Override
        public boolean isActive() {
            return !future.isCancelled() && !future.isDone();
        }
    }
}
