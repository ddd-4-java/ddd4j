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
     * \u6839\u636e\u952e\u8bfb\u53d6\u6620\u5c04\u4e2d\u7684\u503c\u3002
     * @param map \u5f85\u67e5\u8be2\u7684\u6620\u5c04
     * @param key \u952e
     * @param <T> \u503c\u7c7b\u578b
     * @return \u5bf9\u5e94\u503c\uff1b\u6620\u5c04\u4e3a\u7a7a\u6216\u952e\u4e0d\u5b58\u5728\u65f6\u8fd4\u56de null
     */
    public <T> T get(Map map, String key) {
        if (Objects.isNull(map)) {
            return null;
        }
        return (T) map.get(key);
    }

    /**
     * \u6839\u636e\u952e\u8bfb\u53d6\u6620\u5c04\u4e2d\u7684\u503c\uff0c\u7f3a\u5931\u65f6\u8fd4\u56de\u9ed8\u8ba4\u503c\u3002
     * @param map \u5f85\u67e5\u8be2\u7684\u6620\u5c04
     * @param key \u952e
     * @param defaultValue \u9ed8\u8ba4\u503c
     * @param <T> \u503c\u7c7b\u578b
     * @return \u5bf9\u5e94\u503c\u6216\u9ed8\u8ba4\u503c
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
     * @param map  待检索的映射数据
     * @param path 点分隔的属性路径，如 a.b.c
     * @param <T>  属性值的目标类型
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
     * @param map          待检索的映射数据
     * @param keyword      点分隔的属性路径，如 a.b.c
     * @param defaultValue 未命中时使用的默认值
     * @param <T>          返回值类型
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
     * \u5224\u65ad\u6307\u5b9a\u8def\u5f84\u662f\u5426\u5bf9\u5e94\u975e\u7a7a\u503c\u3002
     * @param map \u5f85\u67e5\u8be2\u7684\u6620\u5c04
     * @param keyword \u70b9\u5206\u9694\u7684\u5c5e\u6027\u8def\u5f84
     * @return \u5bf9\u5e94\u503c\u975e\u7a7a\u65f6\u8fd4\u56de true
     */
    public boolean has(Map map, String keyword) {
        return Objects.nonNull(search(map, keyword));
    }

    /**
     * \u5c06\u5bf9\u8c61\u6216\u5bf9\u8c61\u96c6\u5408\u7684\u5b57\u6bb5\u8f6c\u6362\u4e3a\u6620\u5c04\u6570\u636e\u3002
     * @param obj \u5f85\u8f6c\u6362\u7684\u5bf9\u8c61\u6216\u96c6\u5408
     * @param <T> \u8f6c\u6362\u7ed3\u679c\u7c7b\u578b
     * @return \u5bf9\u8c61\u5bf9\u5e94\u7684\u6620\u5c04\u6216\u96c6\u5408\u5bf9\u5e94\u7684\u6620\u5c04\u5217\u8868
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
     * \u7531\u4ea4\u66ff\u6392\u5217\u7684\u952e\u548c\u503c\u521b\u5efa\u6620\u5c04\u3002
     * @param kvPair \u4ea4\u66ff\u6392\u5217\u7684\u5b57\u7b26\u4e32\u952e\u548c\u503c
     * @return \u65b0\u5efa\u7684\u6620\u5c04
     * @throws IllegalArgumentException \u53c2\u6570\u4e2a\u6570\u4e3a\u5947\u6570\u65f6\u629b\u51fa
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
