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
package io.ddd4j.auth.datascope;

import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据权限（DataScope）自动配置。
 *
 * <p>注册数据权限提供者与 Spring 上下文访问 Bean，供数据权限切面在运行期解析数据范围。
 *
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
public class DataScopeAutoConfiguration {

    /**
     * 创建默认的数据权限提供者 Bean。
     *
     * @return 不携带额外配置的空实现 {@link DataScopeProvider}
     */
    @Bean
    public DataScopeProvider dataScopeProvider() {
        return new DataScopeProvider() {
        };
    }

    /**
     * 创建 Spring 上下文访问 Bean，便于在非 Web 环境中获取应用上下文。
     *
     * @return 新建的 {@link SpringContextAwareContext} 实例
     */
    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }

}
