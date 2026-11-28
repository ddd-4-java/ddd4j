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
package io.ddd4j.spring.command.fixture;

import io.ddd4j.spring.config.SpringCommandConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.PlatformTransactionManager;

/** 使用显式命令配置和事务管理器，让样例执行器在真实 Spring 代理内运行。 */
@Import(SpringCommandConfig.class)
@SpringBootConfiguration
@EnableTransactionManagement(proxyTargetClass = true)
@ComponentScan(basePackageClasses = TestApp.class)
public class TestApp {

    /**
     * 注册无资源事务管理器（真实 begin/commit/rollback 生命周期）：使容器存在
     * 单一 {@code PlatformTransactionManager} 候选，激活 {@code @Transactional}
     * 代理——分发事务化由此可被探针用例实证（不引 H2/DataSource）。
     *
     * @return PlatformTransactionManager Bean
     */
    @Bean
    PlatformTransactionManager transactionManager() {
        return new NoopTransactionManager();
    }
}
