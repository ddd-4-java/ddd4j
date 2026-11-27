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

import io.ddd4j.core.health.ReadinessReport;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将框架运行时的启动和关闭动作适配为共享契约。
 */
public final class RuntimeContractAdapter implements RuntimeContract {

    /** \u542f\u52a8\u53d7\u6d4b\u8fd0\u884c\u65f6\u7684\u56de\u8c03\u3002 */
    private final Runnable starter;
    /** \u505c\u6b62\u5e76\u6e05\u7406\u53d7\u6d4b\u8fd0\u884c\u65f6\u7684\u56de\u8c03\u3002 */
    private final Runnable closer;
    /** \u53d7\u6d4b\u8fd0\u884c\u65f6\u5e94\u6ce8\u518c\u7684\u670d\u52a1\u952e\u4e0e\u63a5\u53e3\u7c7b\u578b\u3002 */
    private final Map<String, Class<?>> services;
    /** \u83b7\u53d6\u53d7\u6d4b\u8fd0\u884c\u65f6\u5f53\u524d\u5c31\u7eea\u62a5\u544a\u7684\u56de\u8c03\u3002 */
    private final Supplier<ReadinessReport> readinessSupplier;

    /**
     * 创建运行时契约适配器。
     *
     * @param starter           启动动作
     * @param closer            关闭动作
     * @param services          注册的服务键与接口类型
     * @param readinessSupplier 就绪状态提供者
     */
    public RuntimeContractAdapter(Runnable starter, Runnable closer, Map<String, Class<?>> services,
                                  Supplier<ReadinessReport> readinessSupplier) {
        this.starter = Objects.requireNonNull(starter, "starter must not be null");
        this.closer = Objects.requireNonNull(closer, "closer must not be null");
        this.services = Map.copyOf(Objects.requireNonNull(services, "services must not be null"));
        this.readinessSupplier = Objects.requireNonNull(readinessSupplier, "readinessSupplier must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        starter.run();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Class<?>> services() {
        return services;
    }

    /** {@inheritDoc} */
    @Override
    public ReadinessReport readiness() {
        return readinessSupplier.get();
    }

    /** \u6267\u884c\u5f53\u524d\u88ab\u6d4b\u8fd0\u884c\u65f6\u7684\u6e05\u7406\u56de\u8c03\u3002 */
    @Override
    public void close() {
        closer.run();
    }
}
