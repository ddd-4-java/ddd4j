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
package io.ddd4j.data.external.region;

import java.time.Duration;

/**
 * Cache abstraction used by region templates.
 */
public interface RegionCache {

    RegionCache NONE = new RegionCache() {

        /**
         * 空实现：始终不命中，返回 {@code null}。
         *
         * @param key 缓存键
         * @return 始终为 {@code null}
         */
        @Override
        public String getString(String key) {
            return null;
        }

        /**
         * 空实现：丢弃写入，不做任何缓存。
         *
         * @param key   缓存键
         * @param value 缓存值
         * @param ttl   缓存存活时长
         */
        @Override
        public void set(String key, String value, Duration ttl) {
        }
    };

    /**
     * 获取空实现。
     *
     * @return 不做任何读写缓存的实例
     */
    static RegionCache none() {
        return NONE;
    }

    /**
     * 按键读取缓存字符串。
     *
     * @param key 缓存键
     * @return 缓存值；未命中时返回 {@code null}
     */
    String getString(String key);

    /**
     * 写入带存活时长的缓存字符串。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   缓存存活时长
     */
    void set(String key, String value, Duration ttl);
}
