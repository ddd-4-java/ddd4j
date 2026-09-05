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
package io.ddd4j.spring.properties;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.util.Objects;

/**
 * Spring 属性源后置处理器。
 *
 * <p>将 ddd4j 框架级别的配置属性源（如 application-ddd4j.yml）注入到 Spring 环境。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SpringPropertySourcePostProcessor implements BeanFactoryPostProcessor, Ordered {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 将 ddd4j 配置属性源添加到 Spring Environment
        if (beanFactory instanceof ConfigurableEnvironment env) {
            env.getPropertySources()
                    .addLast(new PropertySource<>("ddd4j-defaults", System.getProperties()) {
                        @Override
                        public Object getProperty(String name) {
                            // 仅处理 ddd4j.* 前缀属性
                            if (Objects.nonNull(name) && name.startsWith("ddd4j.")) {
                                return System.getProperty(name);
                            }
                            return null;
                        }
                    });
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
