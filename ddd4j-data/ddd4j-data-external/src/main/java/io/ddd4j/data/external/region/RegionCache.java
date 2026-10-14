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

    /** 不执行缓存操作的实现。 */
    RegionCache NONE = new RegionCache() {

        @Override
        public String getString(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
        }
    };

    /**
     * 返回不执行缓存操作的实现。
     *
     * @return 空缓存实现
     */
    static RegionCache none() {
        return NONE;
    }

    /**
     * 读取缓存字符串。
     *
     * @param key 缓存键
     * @return 缓存值；未命中时为 null
     */
    String getString(String key);

    /**
     * 写入带有效期的缓存字符串。
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 有效期
     */
    void set(String key, String value, Duration ttl);
}
