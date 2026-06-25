package io.ddd4j.boot.core.context;

import io.ddd4j.boot.core.utils.TransmittableThreadLocal;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地线程上下文：一个本地线程容器
 * 应用可以扩展继承此类，实现如租户上下文等功能
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@UtilityClass
public class ThreadContext {
    // 本地线程变量池
    private ThreadLocal<Map<String, Object>> THREAD_LOCAL_POOL = new TransmittableThreadLocal();

    public <T> T get(String key) {
        Map<String, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.isNull(map) ? null : (T) map.get(key);
    }

    public Map<String, Object> getValues() {
        return THREAD_LOCAL_POOL.get();
    }

    public void setValues(Map<String, Object> values) {
        THREAD_LOCAL_POOL.set(values);
    }

    public <T> T get(String key, T defaultValue) {
        Object o = get(key);
        if (o != null) return (T) o;
        return defaultValue;
    }

    public void set(boolean condition, String key, Object value) {
        if (!condition) return;
        set(key, value);
    }

    public synchronized void set(String key, Object value) {
        if (value == null) {
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
        if (objects == null) return false;
        return objects.containsKey(key);
    }

    public void remove(String key) {
        Map<String, Object> objects = THREAD_LOCAL_POOL.get();
        if (objects != null) {
            objects.remove(key);
            if (objects.isEmpty()) clear();
        }
    }

    public void clear() {
        THREAD_LOCAL_POOL.remove();
    }
}