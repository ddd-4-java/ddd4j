package io.ddd4j.boot.core.context;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础上下文，公共上下文容器
 */
@UtilityClass
public class BaseContext {
    private Map<Object, Object> GLOBAL = new ConcurrentHashMap<>();

    /**
     * 显式注入
     *
     * @param key
     * @param value
     */
    public <K, V> void inject(K key, V value) {
        GLOBAL.put(key, value);
    }

    /**
     * 获取
     *
     * @param key
     * @param <V>
     * @return
     */
    public <K, V> V get(K key) {
        return (V) GLOBAL.get(key);
    }

    /**
     * 是否包含
     *
     * @param key
     * @return
     */
    public <K> boolean contains(K key) {
        return GLOBAL.containsKey(key);
    }

}
