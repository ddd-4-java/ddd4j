package io.ddd4j.core.context;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 本地线程上下文：一个本地线程容器
 * 应用可以扩展继承此类，实现如租户上下文等功能
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
@UtilityClass
public class ThreadContext {

    /**
     * 线程变量池
     */
    private static final ThreadLocal<Map<String, Object>> THREAD_LOCAL_POOL = new TransmittableThreadLocal<>();

    public static <T> T get(String key) {
        Map<String, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.isNull(map) ? null : (T) map.get(key);
    }

    public Map<String, Object> getValues() {
        return THREAD_LOCAL_POOL.get();
    }

    public void setValues(Map<String, Object> values) {
        THREAD_LOCAL_POOL.set(values);
    }

    public static <T> T get(String key, T defaultValue) {
        Object o = get(key);
        if (Objects.nonNull(o)) {
            return (T) o;
        }
        return defaultValue;
    }

    public static void set(boolean condition, String key, Object value) {
        if (!condition) {
            return;
        }
        set(key, value);
    }

    public static void set(String key, Object value) {
        if (Objects.isNull(value)) {
            return;
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            return;
        }
        Map<String, Object> map = THREAD_LOCAL_POOL.get();
        if (Objects.isNull(map)) {
            map = new ConcurrentHashMap<>(4);
        }
        map.put(key, value);
        THREAD_LOCAL_POOL.set(map);
    }

    public boolean contains(String key) {
        Map<String, Object> objects = THREAD_LOCAL_POOL.get();
        if (Objects.isNull(objects)) {
            return false;
        }
        return objects.containsKey(key);
    }

    public static void remove(String key) {
        Map<String, Object> objects = THREAD_LOCAL_POOL.get();
        if (Objects.nonNull(objects)) {
            objects.remove(key);
            if (objects.isEmpty()) {
                clear();
            }
        }
    }

    public static void clear() {
        THREAD_LOCAL_POOL.remove();
    }
}