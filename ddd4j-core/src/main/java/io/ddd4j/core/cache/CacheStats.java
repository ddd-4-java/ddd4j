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
package io.ddd4j.core.cache;

/**
 * 缓存统计信息接口（纯 Java，零框架依赖）。
 *
 * <p>与 Caffeine CacheStats 接口兼容，提供缓存命中率、加载次数等统计信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface CacheStats {

    /**
     * 获取缓存命中次数。
     *
     * @return 命中次数
     */
    long hitCount();

    /**
     * 获取缓存未命中次数。
     *
     * @return 未命中次数
     */
    long missCount();

    /**
     * 获取缓存命中率。
     *
     * @return 命中率（0.0 - 1.0）
     */
    double hitRate();

    /**
     * 获取缓存加载次数。
     *
     * @return 加载次数
     */
    long loadCount();

    /**
     * 获取缓存淘汰次数。
     *
     * @return 淘汰次数
     */
    long evictionCount();

}
