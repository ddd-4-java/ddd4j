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
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;

import java.util.Objects;


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
@DefaultBean
public class QuarkusViewScheduler implements ViewScheduler {

    /**
     * Quartz JobDataMap 中任务标识的键名
     */
    static final String TASK_KEY = "ddd4j.task";

    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        try {
            return NativeQuartz.schedule(viewName, cron, task);
        } catch (NoClassDefFoundError missingQuartz) {
            throw new IllegalStateException("Quartz Scheduler is unavailable; add the quarkus-quartz extension", missingQuartz);
        }
    }

    // 将选装 Quartz 的链接边界放在真实 schedule 调用内，ArC 发现外层 Bean 时不加载 Quartz 类型。
    private static final class NativeQuartz {
        private static ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
            Instance<io.quarkus.quartz.QuartzScheduler> schedulers = CDI.current()
                    .select(io.quarkus.quartz.QuartzScheduler.class);
            if (!schedulers.isResolvable()) {
                throw new IllegalStateException("A unique Quarkus QuartzScheduler is required; add quarkus-quartz");
            }
            Scheduler scheduler = schedulers.get().getScheduler();
            if (Objects.isNull(scheduler)) {
                throw new IllegalStateException("Quartz is disabled or not initialized for programmatic projection scheduling");
            }
            QuartzViewScheduleHandle handle = QuartzViewScheduleHandle.schedule(scheduler, viewName, cron, task);
            log.info("Scheduled view '{}' with cron '{}'", viewName, cron);
            return handle;
        }
    }
}
