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
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.spring.context.SpringContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.extension.context.SpringContextAwareContext;
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

    /**
     * \u6c47\u96c6 Spring \u5bb9\u5668\u4e2d\u7684\u5c31\u7eea\u68c0\u67e5\u9879\u3002
     * @param readinessContributors \u5c31\u7eea\u68c0\u67e5\u9879\u63d0\u4f9b\u8005
     * @return \u8fd0\u884c\u65f6\u5c31\u7eea\u6ce8\u518c\u8868
     */
    @Bean
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

    public RuntimeReadinessRegistry runtimeReadinessRegistry(
            ObjectProvider<ReadinessContributor> readinessContributors) {
        return new RuntimeReadinessRegistry(readinessContributors.orderedStream().toList());
    }

    /**
     * springContext() 方法。
     * @return 方法执行结果。
     */
    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

    /**
     * springContextAwareContext() 方法。
     * @return 方法执行结果。
     */
    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }
}
