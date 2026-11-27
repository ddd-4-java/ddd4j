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

import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Quarkus CQRS 默认 CDI 生产者。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusCqrsProducer {
    /**
     * \u5728\u542f\u52a8\u4e8b\u4ef6\u4e2d\u9a8c\u8bc1\u5f53\u524d\u5bb9\u5668\u7684 CQRS \u7ec4\u4ef6\u88c5\u914d\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void validateBootstrap(@jakarta.enterprise.event.Observes @jakarta.annotation.Priority(800) io.quarkus.runtime.StartupEvent event) {
        String bootstrap = ConfigProvider.getConfig().getOptionalValue(
                "ddd4j.quarkus.cqrs.projection.bootstrap-cron", String.class).orElse("off").stripLeading();
        if (!"off".equalsIgnoreCase(bootstrap) && !"disabled".equalsIgnoreCase(bootstrap)) {
            throw new IllegalStateException("projection.bootstrap-cron is reserved and must remain off or disabled");
        }
    }


    /**
     * 创建默认的内存投影位置仓库。
     *
     * @return 投影位置仓库
     */
    public ProjectionPositionRepository projectionPositionRepository() {
        return new InMemoryProjectionPositionRepository();
    }

    /**
     * 按真实 ORM 选择旧 JPA 或内存默认；durable 禁止无持久仓储的回退。
     *
     * @param entityManagers  当前 ORM 实体管理器
     * @param jpaRepositories 旧公开 JPA 适配器
     * @return 当前唯一位置仓储
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionPositionRepository selectedPositionRepository(Instance<EntityManager> entityManagers,
                                                                   Instance<QuarkusJpaProjectionPositionRepository> jpaRepositories) {
        if (entityManagers.isAmbiguous()) {
            throw new IllegalStateException("Multiple EntityManager beans are available for projection storage");
        }
        if (entityManagers.isResolvable()) {
            return jpaRepositories.get();
        }
        if ("durable".equals(ConfigProvider.getConfig().getOptionalValue(
                "ddd4j.quarkus.cqrs.projection.mode", String.class).orElse("off"))) {
            throw new IllegalStateException("Durable projection requires a persistent ProjectionPositionRepository or EntityManager");
        }
        return projectionPositionRepository();
    }

    /**
     * 创建投影服务。
     *
     * @param repository 投影位置仓库
     * @return 投影服务
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * 创建投影执行器。
     *
     * @param projectionService 投影服务
     * @param chunkReaders      事件块读取器
     * @return 投影执行器
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionRunner<Object> projectionRunner(
            ProjectionService projectionService,
            Instance<EventChunkReader<Object>> chunkReaders) {
        EventChunkReader<Object> chunkReader = chunkReaders.isResolvable()
                ? chunkReaders.get()
                : new NoopEventChunkReader<>();
        return new ProjectionRunner<>(projectionService, chunkReader);
    }
}
