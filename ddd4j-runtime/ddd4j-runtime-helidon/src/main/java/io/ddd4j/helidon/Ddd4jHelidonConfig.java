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
package io.ddd4j.helidon;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Objects;
import java.util.Optional;

/**
 * Helidon MP 配置访问边界，避免核心代码依赖 MicroProfile Config。
 */
public final class Ddd4jHelidonConfig {

    /** Helidon \u5bb9\u5668\u63d0\u4f9b\u7684\u914d\u7f6e\u67e5\u8be2\u5165\u53e3\u3002 */
    private final Config config;

    /** \u4f7f\u7528\u5f53\u524d MicroProfile \u914d\u7f6e\u521b\u5efa\u8bbf\u95ee\u5668\u3002 */
    public Ddd4jHelidonConfig() {
        this(ConfigProvider.getConfig());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u914d\u7f6e\u6e90\u521b\u5efa\u8bbf\u95ee\u5668\u3002
     * @param config MicroProfile \u914d\u7f6e\u6e90
     */
    public Ddd4jHelidonConfig(Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * \u8bfb\u53d6\u53ef\u9009\u914d\u7f6e\u503c\u3002
     * @param <T> \u914d\u7f6e\u503c\u7c7b\u578b
     * @param name \u914d\u7f6e\u540d\u79f0
     * @param type \u914d\u7f6e\u503c\u7c7b\u578b
     * @return \u53ef\u9009\u914d\u7f6e\u503c
     */
    public <T> Optional<T> value(String name, Class<T> type) {
        return config.getOptionalValue(name, type);
    }
}
