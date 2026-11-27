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
package io.ddd4j.quarkus.cqrs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runnable 暂存容器。
 * <p>
 * 用于在 Quartz Job 和调用方之间传递 {@link Runnable} 任务实例，
 * 以 {@code identity} 为键进行存取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
final class RunnableHolder {

    private static final ConcurrentMap<String, Runnable> TASKS = new ConcurrentHashMap<>();

    private RunnableHolder() {
    }

    static void put(String identity, Runnable task) {
        TASKS.put(identity, task);
    }

    static Runnable get(String identity) {
        return TASKS.get(identity);
    }

    static void remove(String identity) {
        TASKS.remove(identity);
    }
}
