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

import cn.hutool.core.map.MapUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Map工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
@Slf4j
public class MapKit extends MapUtil {

    /**
     * 根据键读取映射中的值。
     *
     * @param map 待查询的映射
     * @param key 键
     * @param <T> 值类型
     * @return 对应值；映射为空或键不存在时返回 null
     */
    public <T> T get(Map map, String key) {
        if (Objects.isNull(map)) {
            return null;
        }
        return (T) map.get(key);
    }

    /**
     * 根据键读取映射中的值，缺失时返回默认值。
     *
     * @param map 待查询的映射
     * @param key 键
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 对应值或默认值
     */
    public <T> T get(Map map, String key, T defaultValue) {
        T result = get(map, key);
        if (Objects.isNull(result)) {
            return defaultValue;
        }
        return result;
    }

    /**
     * 根据点分隔路径读取嵌套映射中的值。
     *
     * @param map 待检索的映射数据
     * @param path 点分隔的属性路径，如 a.b.c
     * @param <T> 属性值的目标类型
     * @return 路径命中的值，未命中时返回 null
     */
    public <T> T search(Map map, String path) {
        if (Objects.isNull(map)) {
            return null;
        }
        String[] keyArray = path.split("\\.");
        if (keyArray.length == 1) {
            return get(map, keyArray[0]);
        } else {
            Map value = null;
            for (int i = 0; i < keyArray.length - 1; i++) {
                if (Objects.isNull(value)) {
                    value = get(map, keyArray[i]);
                } else if (value.containsKey(keyArray[i])) {
                    value = (Map) value.get(keyArray[i]);
                }
            }
            if (Objects.isNull(value)) {
                return null;
            }
            return (T) value.get(keyArray[keyArray.length - 1]);
        }
    }

    /**
     * 根据点分隔路径读取嵌套映射中的值，并提供默认值。
     *
     * @param map 待检索的映射数据
     * @param keyword 点分隔的属性路径，如 a.b.c
     * @param defaultValue 未命中时使用的默认值
     * @param <T> 返回值类型
     * @return 路径命中的值，未命中时返回默认值
     */
    public <T> T search(Map map, String keyword, T defaultValue) {
        T result = search(map, keyword);
        if (Objects.isNull(result)) {
            return defaultValue;
        }
        return result;
    }

    /**
     * 判断指定路径是否对应非空值。
     *
     * @param map 待查询的映射
     * @param keyword 点分隔的属性路径
     * @return 对应值非空时返回 true
     */
    public boolean has(Map map, String keyword) {
        return Objects.nonNull(search(map, keyword));
    }

    /**
     * 将对象或对象集合的字段转换为映射数据。
     *
     * @param obj 待转换的对象或集合
     * @param <T> 转换结果类型
     * @return 对象对应的映射或集合对应的映射列表
     */
    public <T> T of(Object obj) {
        if (obj instanceof Collection) {
            Collection collection = (Collection) obj;
            List<Map> list = new ArrayList<>();
            for (Object o : collection) {
                Map map = new HashMap();
                Class<?> clazz = o.getClass();
                Field[] fields = clazz.getDeclaredFields();
                // 遍历对象的字段
                for (Field field : fields) {
                    try {
                        // 设置字段可访问
                        field.setAccessible(true);
                        // 获取字段名称和值，并存储到 Map 中
                        String fieldName = field.getName();
                        Object fieldValue = field.get(o);
                        map.put(fieldName, fieldValue);
                    } catch (IllegalAccessException e) {
                        log.warn("Read field failed: {}", field.getName(), e);
                    }
                }
                list.add(map);
            }
            return (T) list;
        } else {
            Map map = new HashMap();
            Class<?> clazz = obj.getClass();
            Field[] fields = clazz.getDeclaredFields();
            // 遍历对象的字段
            for (Field field : fields) {
                try {
                    // 设置字段可访问
                    field.setAccessible(true);
                    // 获取字段名称和值，并存储到 Map 中
                    String fieldName = field.getName();
                    Object fieldValue = field.get(obj);
                    map.put(fieldName, fieldValue);
                } catch (IllegalAccessException e) {
                    log.warn("Read field failed: {}", field.getName(), e);
                }
            }
            return (T) map;
        }
    }

    /**
     * 由交替排列的键和值创建映射。
     *
     * @param kvPair 交替排列的字符串键和值
     * @return 新建的映射
     * @throws IllegalArgumentException 参数个数为奇数时抛出
     */
    public Map<String, Object> ofMap(Object... kvPair) {
        if (kvPair.length % 2 != 0) {
            throw new IllegalArgumentException("kvPair's must not be single");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kvPair.length - 1; i += 2) {
            map.put((String) kvPair[i], kvPair[i + 1]);
        }
        return map;
    }

}
