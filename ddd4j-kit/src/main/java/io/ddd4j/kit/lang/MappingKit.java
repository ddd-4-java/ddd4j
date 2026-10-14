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
package io.ddd4j.kit.lang;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于对象映射，按 biz 隔离
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public final class MappingKit {
    // Bean容器
    private final Map<String, Map<Object, Object>> BEAN_MAPPINGS = new ConcurrentHashMap<>();

    /**
     * 为业务标识登记键值映射。
     *
     * @param biz 业务标识
     * @param key 映射键
     * @param value 映射值
     * @param <K> 映射键类型
     * @param <V> 映射值类型
     */
    public <K, V> void map(String biz, K key, V value) {
        Map<Object, Object> mappings = BEAN_MAPPINGS.computeIfAbsent(biz, k -> new ConcurrentHashMap<>());
        mappings.put(key, value);
    }

    /**
     * 读取指定业务标识下的映射值。
     *
     * @param field 业务标识
     * @param source 映射键
     * @return 对应映射值；不存在时返回 null
     * @param <K> 映射键类型
     * @param <V> 映射值类型
     */
    public static <K, V> V get(String field, K source) {
        Map<Object, Object> mappings = BEAN_MAPPINGS.get(field);
        if (Objects.isNull(mappings)) {
            return null;
        }
        return (V) mappings.get(source);
    }

}