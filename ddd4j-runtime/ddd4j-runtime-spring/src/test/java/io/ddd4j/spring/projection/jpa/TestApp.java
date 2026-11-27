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
package io.ddd4j.spring.projection.jpa;

import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 集成测试引导应用（Task 7.2，H2 内存库 IT 用）。
 *
 * <p>刻意不使用 {@code @SpringBootApplication}：本模块不依赖 Boot 运行时，
 * 测试栈以最小装配组合出真实容器——自动配置（DataSource/JPA/事务管理）＋
 * {@code @EntityScan} 扫描 {@link ProjectionPositionEntity}，显式注册
 * {@link JpaProjectionPositionRepository}，Spring Data 仓储接口由 JpaRepositories
 * 自动配置从本类所在包向下发现 {@link SpringDataProjectionPositionRepository}
 * （与 {@code ddd4j-data-event-store-jpa} 的 TestApp 同款模式）。
 *
 * <p>数据源由 {@code application-test.yml}（H2 内存库，MODE=PostgreSQL）提供。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "io.ddd4j.data.projection.jpa")
@EnableJpaRepositories(basePackages = "io.ddd4j.spring.projection.jpa")
class TestApp {
    /**
     * \u521b\u5efa <code>new JpaProjectionPositionRepository</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param repository \u5f53\u524d\u6d4b\u8bd5\u8bbf\u95ee\u7684\u4ed3\u50a8\u5b9e\u4f8b
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new JpaProjectionPositionRepository(repository)</code>
     */
    @Bean
    JpaProjectionPositionRepository jpaProjectionPositionRepository(SpringDataProjectionPositionRepository repository) {
        return new JpaProjectionPositionRepository(repository);
    }
}
