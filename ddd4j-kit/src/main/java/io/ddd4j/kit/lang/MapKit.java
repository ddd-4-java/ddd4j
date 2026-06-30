package io.ddd4j.kit.lang;

import cn.hutool.core.map.MapUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.UtilityClass;

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

    public <T> T get(Map map, String key) {
        if (java.util.Objects.isNull(map)) {
            return null;
        }
        return (T) map.get(key);
    }

    public <T> T get(Map map, String key, T defaultValue) {
        T result = get(map, key);
        if (java.util.Objects.isNull(result)) {
            return defaultValue;
        }
        return result;
    }

    /**
     * 根据搜索字符串遍历Json，如：{"a":{"b":{"c":"d"}}}，入参为：a.b.c，返回：d
     *
     * @param path
     * @param <T>
     * @return
     */
    public <T> T search(Map map, String path) {
        if (java.util.Objects.isNull(map)) {
            return null;
        }
        String[] keyArray = path.split("\\.");
        if (keyArray.length == 1) {
            return get(map, keyArray[0]);
        } else {
            Map value = null;
            for (int i = 0; i < keyArray.length - 1; i++) {
                if (java.util.Objects.isNull(value)) {
                    value = get(map, keyArray[i]);
                } else if (value.containsKey(keyArray[i])) {
                    value = (Map) value.get(keyArray[i]);
                }
            }
            if (java.util.Objects.isNull(value)) {
                return null;
            }
            return (T) value.get(keyArray[keyArray.length - 1]);
        }
    }

    /**
     * 根据搜索字符串遍历Json，如：{"a":{"b":{"c":"d"}}}，入参为：a.b.c，返回：d
     *
     * @param keyword
     * @param <T>
     * @return
     */
    public <T> T search(Map map, String keyword, T defaultValue) {
        T result = search(map, keyword);
        if (java.util.Objects.isNull(result)) {
            return defaultValue;
        }
        return result;
    }

    public boolean has(Map map, String keyword) {
        return java.util.Objects.nonNull(search(map, keyword));
    }

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
