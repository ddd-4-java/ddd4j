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
 * @since 1.0.x
 */
final class RunnableHolder {

    /** \u4f9b Quartz \u4efb\u52a1\u6309\u8eab\u4efd\u5b9a\u4f4d\u5b9e\u9645\u6295\u5f71\u56de\u8c03\u7684\u5171\u4eab\u6ce8\u518c\u8868\u3002 */
    private static final ConcurrentMap<String, Runnable> TASKS = new ConcurrentHashMap<>();

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>RunnableHolder</code> \u5b9e\u4f8b\u3002 */
    private RunnableHolder() {
    }

    /**
     * \u628a\u6295\u5f71\u4efb\u52a1\u767b\u8bb0\u5230 Quartz \u53ef\u4f9d\u636e\u4efb\u52a1\u6807\u8bc6\u8bfb\u53d6\u7684\u5185\u5b58\u8868\u3002
     * @param identity \u7528\u4e8e\u8ba9\u8c03\u5ea6\u4efb\u52a1\u627e\u5230\u89c6\u56fe\u56de\u8c03\u7684\u552f\u4e00\u6807\u8bc6
     * @param task \u5b9e\u9645\u6267\u884c\u4e00\u6b21\u6295\u5f71\u7684\u56de\u8c03
     */
    static void put(String identity, Runnable task) {
        TASKS.put(identity, task);
    }

    /**
     * \u8bfb\u53d6\u6307\u5b9a\u8c03\u5ea6\u4efb\u52a1\u5bf9\u5e94\u7684\u6295\u5f71\u56de\u8c03\u3002
     * @param identity \u7528\u4e8e\u8ba9\u8c03\u5ea6\u4efb\u52a1\u627e\u5230\u89c6\u56fe\u56de\u8c03\u7684\u552f\u4e00\u6807\u8bc6
     * @return \u5df2\u767b\u8bb0\u7684\u6295\u5f71\u4efb\u52a1\uff0c\u6ca1\u6709\u767b\u8bb0\u65f6\u8fd4\u56de\u7a7a
     */
    static Runnable get(String identity) {
        return TASKS.get(identity);
    }

    /**
     * \u4ece\u4efb\u52a1\u8868\u79fb\u9664\u6307\u5b9a\u8eab\u4efd\u7684\u539f\u6295\u5f71\u56de\u8c03\u3002
     * @param identity \u7528\u4e8e\u8ba9\u8c03\u5ea6\u4efb\u52a1\u627e\u5230\u89c6\u56fe\u56de\u8c03\u7684\u552f\u4e00\u6807\u8bc6
     */
    static void remove(String identity) {
        TASKS.remove(identity);
    }
}
