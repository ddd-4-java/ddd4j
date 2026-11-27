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
package io.ddd4j.core.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 具备失败回滚和关闭清理能力的全局 SPI 注册作用域。
 */
public final class SpiRegistrationScope implements AutoCloseable {

    /**
     * 等待按顺序安装的服务注册。
     */
    private final List<Registration<?>> registrations = new ArrayList<>();
    /**
     * 已安装、关闭时按反序恢复的服务注册。
     */
    private final List<Registration<?>> installed = new ArrayList<>();
    /**
     * 当前注册作用域是否已执行安装。
     */
    private boolean started;

    /**
     * 登记作用域启动时要安装的服务。
     *
     * @param <T>     服务类型
     * @param key     服务标识
     * @param type    服务接口类型
     * @param service 服务实例
     * @return 当前登记作用域
     */
    public synchronized <T> SpiRegistrationScope register(String key, Class<T> type, T service) {
        if (started) {
            throw new IllegalStateException("SPI scope has already started");
        }
        registrations.add(new Registration<>(key, type, service));
        return this;
    }

    /**
     * 安装已登记的服务；重复调用不再安装。
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            for (Registration<?> registration : registrations) {
                registration.install();
                installed.add(registration);
            }
            started = true;
        } catch (RuntimeException exception) {
            rollback();
            throw exception;
        }
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>rollback()</code>。</p>
     */
    @Override
    public synchronized void close() {
        rollback();
        started = false;
        ThreadContext.clear();
    }

    /**
     * 按安装的逆序注销已装配服务，恢复仍由本作用域持有的原注册。
     */
    private void rollback() {
        for (int index = installed.size() - 1; index >= 0; index--) {
            installed.get(index).uninstall();
        }
        installed.clear();
    }

    /**
     * 保存 SPI 注册的键、服务类型、实例及退出时需要恢复的原服务。
     *
     * @param <T> 注册的 SPI 服务类型
     */
    private static final class Registration<T> {

        /**
         * 参与 SPI 注册及查找的键。
         */
        private final String key;
        /**
         * 当前登记的 SPI 服务类型。
         */
        private final Class<T> type;
        /**
         * 当前注册作用域提供的服务实例。
         */
        private final T service;
        /**
         * 本作用域登记前同一个键上的服务实例。
         */
        private Object previous;

        /**
         * 保存待安装的 SPI 服务及其类型信息。
         *
         * @param key     服务注册键
         * @param type    可用于类型检查的 SPI 服务类型
         * @param service 本作用域要注册的服务实例
         */
        private Registration(String key, Class<T> type, T service) {
            this.key = Objects.requireNonNull(key, "key must not be null");
            this.type = Objects.requireNonNull(type, "type must not be null");
            this.service = Objects.requireNonNull(service, "service must not be null");
        }

        /**
         * 保存共享键上的旧值并登记当前作用域提供的服务实例。
         */
        private void install() {
            // The shared key can be occupied by a legacy, untyped value.
            previous = BaseContext.get(key);
            BaseContext.inject(key, type, service);
        }

        /**
         * 当前服务仍拥有共享键时恢复旧值，原来无值时移除键。
         */
        private void uninstall() {
            if (BaseContext.get(key) == service) {
                if (Objects.nonNull(previous)) {
                    BaseContext.inject(key, previous);
                } else {
                    BaseContext.remove(key);
                }
            }
            previous = null;
        }
    }
}
