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

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.quarkus.arc.DefaultBean;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus CQRS 投影拉取服务。
 * <p>
 * 由视图管理器按原 cron 配置注册原生 Quartz 任务，保留旧子类拉取钩子。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
@DefaultBean
public class QuarkusJpaProjectionService {

    /**
     * 投影位置仓储实例
     */
    @Inject
    Instance<ProjectionPositionRepository> projectionPositionRepository;

    // 原生禁用声明使 NORMAL 模式保留 Quartz 编程式生命周期；此声明不创建业务任务。
    @Scheduled(cron = "${ddd4j.quarkus.cqrs.projection.bootstrap-cron:off}")
    void scheduled() {
        runOnce();
    }

    /** 在事务中执行一次待处理投影拉取。 */
    @Transactional
    public void runOnce() {
        if (projectionPositionRepository.isUnsatisfied()) {
            log.debug("ProjectionPositionRepository not provided, skip projection");
            return;
        }
        var positions = projectionPositionRepository.get().findAll();
        if (positions.isEmpty()) {
            log.debug("No projection positions registered, skip");
            return;
        }
        for (ProjectionPosition position : positions) {
            try {
                pullAndApply(position.getStreamId());
            } catch (Exception ex) {
                log.warn("Failed to pull and apply projection for stream '{}'", position.getStreamId(), ex);
            }
        }
    }

    /**
     * 拉取并应用指定流的事件。
     *
     * @param streamId 投影流标识
     */
    protected void pullAndApply(String streamId) {
        log.debug("pullAndApply for stream '{}' (override me to implement actual projection)", streamId);
    }
}
