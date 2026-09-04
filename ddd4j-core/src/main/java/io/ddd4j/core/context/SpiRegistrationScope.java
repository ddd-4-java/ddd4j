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
import java.util.Optional;

/**
 * 具备失败回滚和关闭清理能力的全局 SPI 注册作用域。
 */
public final class SpiRegistrationScope implements AutoCloseable {

    private final List<Registration<?>> registrations = new ArrayList<>();
    private final List<Registration<?>> installed = new ArrayList<>();
    private boolean started;

    public synchronized <T> SpiRegistrationScope register(String key, Class<T> type, T service) {
        if (started) {
            throw new IllegalStateException("SPI scope has already started");
        }
        registrations.add(new Registration<>(key, type, service));
        return this;
    }

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

    @Override
    public synchronized void close() {
        rollback();
        started = false;
        ThreadContext.clear();
    }

    private void rollback() {
        for (int index = installed.size() - 1; index >= 0; index--) {
            installed.get(index).uninstall();
        }
        installed.clear();
    }

    private static final class Registration<T> {

        private final String key;
        private final Class<T> type;
        private final T service;
        private Optional<T> previous = Optional.empty();

        private Registration(String key, Class<T> type, T service) {
            this.key = Objects.requireNonNull(key, "key must not be null");
            this.type = Objects.requireNonNull(type, "type must not be null");
            this.service = Objects.requireNonNull(service, "service must not be null");
        }

        private void install() {
            previous = BaseContext.get(key, type);
            BaseContext.inject(key, type, service);
        }

        private void uninstall() {
            BaseContext.get(key, type)
                    .filter(current -> current == service)
                    .ifPresent(ignored -> {
                        if (previous.isPresent()) {
                            BaseContext.inject(key, type, previous.get());
                        } else {
                            BaseContext.remove(key);
                        }
                    });
            previous = Optional.empty();
        }
    }
}
