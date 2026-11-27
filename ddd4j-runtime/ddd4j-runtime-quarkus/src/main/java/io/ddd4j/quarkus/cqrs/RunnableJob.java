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
 * @since 1.0.x
 */
public class RunnableJob implements Job {

    /**
     * \u4ece Quartz \u4efb\u52a1\u4e0a\u4e0b\u6587\u627e\u51fa\u767b\u8bb0\u7684\u6295\u5f71\u56de\u8c03\u5e76\u6267\u884c\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u4f20\u5165\u7684\u6267\u884c\u6216\u5bb9\u5668\u4e0a\u4e0b\u6587
     */
    @Override
    public void execute(JobExecutionContext context) {
        String identity = context.getJobDetail().getJobDataMap().getString(QuarkusViewScheduler.TASK_KEY);
        Runnable task = RunnableHolder.get(identity);
        if (Objects.nonNull(task)) {
            task.run();
        }
    }
}
