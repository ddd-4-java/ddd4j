package io.ddd4j.core.util;

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
    /** \u6309\u6620\u5c04\u540d\u79f0\u4fdd\u5b58\u7684\u5bf9\u8c61\u952e\u503c\u8f6c\u6362\u8868\u3002 */
    private final Map<String, Map<Object, Object>> BEAN_MAPPINGS = new ConcurrentHashMap<>();

    /**
     * \u4e3a\u4e1a\u52a1\u6807\u8bc6\u767b\u8bb0\u952e\u503c\u6620\u5c04\u3002
     * @param biz \u4e1a\u52a1\u6807\u8bc6
     * @param key \u6620\u5c04\u952e
     * @param value \u6620\u5c04\u503c
     * @param <K> \u6620\u5c04\u952e\u7c7b\u578b
     * @param <V> \u6620\u5c04\u503c\u7c7b\u578b
     */
    public <K, V> void map(String biz, K key, V value) {
        Map<Object, Object> mappings = BEAN_MAPPINGS.computeIfAbsent(biz, k -> new ConcurrentHashMap<>());
        mappings.put(key, value);
    }

    /**
     * \u8bfb\u53d6\u6307\u5b9a\u4e1a\u52a1\u6807\u8bc6\u4e0b\u7684\u6620\u5c04\u503c\u3002
     * @param field \u4e1a\u52a1\u6807\u8bc6
     * @param source \u6620\u5c04\u952e
     * @param <K> \u6620\u5c04\u952e\u7c7b\u578b
     * @param <V> \u6620\u5c04\u503c\u7c7b\u578b
     * @return \u5bf9\u5e94\u6620\u5c04\u503c\uff1b\u4e0d\u5b58\u5728\u65f6\u8fd4\u56de null
     */
    public static <K, V> V get(String field, K source) {
        Map<Object, Object> mappings = BEAN_MAPPINGS.get(field);
        if (Objects.isNull(mappings)) {
            return null;
        }
        return (V) mappings.get(source);
    }

}