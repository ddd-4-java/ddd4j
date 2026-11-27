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

    /**
     * 启动受测运行时的回调。
     */
    private final Runnable starter;
    /**
     * 停止并清理受测运行时的回调。
     */
    private final Runnable closer;
    /**
     * 受测运行时应注册的服务键与接口类型。
     */
    private final Map<String, Class<?>> services;
    /**
     * 获取受测运行时当前就绪报告的回调。
     */
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

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>此实现调用 <code>starter.run()</code>。</p>
     */
    @Override
    public void start() {
        starter.run();
    }

    /**
     * 读取<code>services</code>提供给调用方。
     *
     * @return 实际返回<code>services</code>，供调用方取得当前值
     */
    @Override
    public Map<String, Class<?>> services() {
        return services;
    }

    /**
     * 通过<code>readinessSupplier.get()</code>处理当前调用。
     *
     * @return 实际返回<code>readinessSupplier.get()</code>
     */
    @Override
    public ReadinessReport readiness() {
        return readinessSupplier.get();
    }

    /**
     * 执行当前被测运行时的清理回调。
     */
    @Override
    public void close() {
        closer.run();
    }
}
