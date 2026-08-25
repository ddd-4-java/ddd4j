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

    private final Config config;

    public Ddd4jHelidonConfig() {
        this(ConfigProvider.getConfig());
    }

    public Ddd4jHelidonConfig(Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public <T> Optional<T> value(String name, Class<T> type) {
        return config.getOptionalValue(name, type);
    }
}
