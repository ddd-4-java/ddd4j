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
package io.ddd4j.spring.config;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.spring.context.SpringContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring 核心配置
 * <p>
 * 注册 {@link SpringContext} 为 Spring Bean，使其不依赖 web 模块也能激活。
 *
 * <p>迁移说明：原 ddd4j-core 模块中的 {@code io.ddd4j.core.config.BaseCoreConfig}
 * 已废弃，请使用本类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(exposeProxy = true)
public class SpringCoreConfig {

    @Bean
    public RuntimeReadinessRegistry runtimeReadinessRegistry(
            ObjectProvider<ReadinessContributor> readinessContributors) {
        return new RuntimeReadinessRegistry(readinessContributors.orderedStream().toList());
    }

    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }
}
