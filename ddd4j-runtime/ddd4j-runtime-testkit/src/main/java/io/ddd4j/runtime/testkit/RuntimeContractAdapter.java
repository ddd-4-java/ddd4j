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
package io.ddd4j.runtime.testkit;

import java.util.Collections;
import java.util.HashMap;
import io.ddd4j.core.health.ReadinessReport;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将框架运行时的启动和关闭动作适配为共享契约。
 */
public final class RuntimeContractAdapter implements RuntimeContract {

    private final Runnable starter;
    private final Runnable closer;
    private final Map<String, Class<?>> services;
    private final Supplier<ReadinessReport> readinessSupplier;

    public RuntimeContractAdapter(Runnable starter, Runnable closer, Map<String, Class<?>> services,
                                  Supplier<ReadinessReport> readinessSupplier) {
        this.starter = Objects.requireNonNull(starter, "starter must not be null");
        this.closer = Objects.requireNonNull(closer, "closer must not be null");
        this.services = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(services, "services must not be null")));
        this.readinessSupplier = Objects.requireNonNull(readinessSupplier, "readinessSupplier must not be null");
    }

    @Override
    public void start() {
        starter.run();
    }

    @Override
    public Map<String, Class<?>> services() {
        return services;
    }

    @Override
    public ReadinessReport readiness() {
        return readinessSupplier.get();
    }

    @Override
    public void close() {
        closer.run();
    }
}
