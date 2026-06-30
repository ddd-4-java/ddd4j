package io.ddd4j.core.util;

import lombok.experimental.UtilityClass;

import java.util.Map;
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

    public <K, V> void map(String biz, K key, V value) {
        Map<Object, Object> mappings = BEAN_MAPPINGS.computeIfAbsent(biz, k -> new ConcurrentHashMap<>());
        mappings.put(key, value);
    }

    public static <K, V> V get(String field, K source) {
        Map<Object, Object> mappings = BEAN_MAPPINGS.get(field);
        if (java.util.Objects.isNull(mappings)) {
            return null;
        }
        return (V) mappings.get(source);
    }

}