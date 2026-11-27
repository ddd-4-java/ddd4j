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

    /**
     * 供 Quartz 任务按身份定位实际投影回调的共享注册表。
     */
    private static final ConcurrentMap<String, Runnable> TASKS = new ConcurrentHashMap<>();

    /**
     * 限制调用方直接创建 <code>RunnableHolder</code> 实例。
     */
    private RunnableHolder() {
    }

    /**
     * 把投影任务登记到 Quartz 可依据任务标识读取的内存表。
     *
     * @param identity 用于让调度任务找到视图回调的唯一标识
     * @param task     实际执行一次投影的回调
     */
    static void put(String identity, Runnable task) {
        TASKS.put(identity, task);
    }

    /**
     * 读取指定调度任务对应的投影回调。
     *
     * @param identity 用于让调度任务找到视图回调的唯一标识
     * @return 已登记的投影任务，没有登记时返回空
     */
    static Runnable get(String identity) {
        return TASKS.get(identity);
    }

    /**
     * 从任务表移除指定身份的原投影回调。
     *
     * @param identity 用于让调度任务找到视图回调的唯一标识
     */
    static void remove(String identity) {
        TASKS.remove(identity);
    }
}
