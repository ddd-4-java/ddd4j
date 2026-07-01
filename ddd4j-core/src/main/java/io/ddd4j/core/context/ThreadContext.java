package io.ddd4j.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import io.ddd4j.core.subject.Subject;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地线程上下文：一个本地线程容器
 * 应用可以扩展继承此类，实现如租户上下文等功能
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@UtilityClass
public class ThreadContext {

    public static final String SECURITY_MANAGER_KEY = ThreadContext.class.getName() + "_SECURITY_MANAGER_KEY";
    public static final String SUBJECT_KEY = ThreadContext.class.getName() + "_SUBJECT_KEY";

    /**
     * 线程变量池
     */
    private static final ThreadLocal<Map<Object, Object>> THREAD_LOCAL_POOL = new TransmittableThreadLocal<>();

    // ========================= Object Key API =========================

    /**
     * Returns the ThreadLocal Map. This Map is used internally to bind objects
     * to the current thread by storing each object under a unique key.
     *
     * @return the map of bound resources
     */
    public static Map<Object, Object> getResources() {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.isNull(map) ? Collections.emptyMap() : new HashMap<>(map);
    }

    /**
     * Allows a caller to explicitly set the entire resource map.
     *
     * @param newResources the resources to replace the existing resources.
     */
    public static void setResources(Map<Object, Object> newResources) {
        if (Objects.isNull(newResources)) {
            return;
        }
        ensureResourcesInitialized();
        THREAD_LOCAL_POOL.get().clear();
        THREAD_LOCAL_POOL.get().putAll(newResources);
    }

    /**
     * Returns the value bound in the ThreadContext under the specified key, or {@code null}.
     *
     * @param key the map key to use to lookup the value
     * @return the value bound in the ThreadContext under the specified key, or {@code null}
     */
    public static Object get(Object key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        if (Objects.isNull(map)) {
            return null;
        }
        Object value = map.get(key);
        if (Objects.nonNull(value) && log.isTraceEnabled()) {
            log.trace("Retrieved value of type [{}] for key [{}] bound to thread [{}]",
                    value.getClass().getName(), key, Thread.currentThread().getName());
        }
        return value;
    }

    /**
     * Binds value for the given key to the current thread.
     * <p>A {@code null} value has the same effect as if {@code remove} was called for the given key.</p>
     *
     * @param key   The key with which to identify the value.
     * @param value The value to bind to the thread.
     */
    public static void put(Object key, Object value) {
        if (Objects.isNull(key)) {
            throw new IllegalArgumentException("key cannot be null");
        }
        if (Objects.isNull(value)) {
            remove(key);
            return;
        }
        ensureResourcesInitialized();
        THREAD_LOCAL_POOL.get().put(key, value);
        if (log.isTraceEnabled()) {
            log.trace("Bound value of type [{}] for key [{}] to thread [{}]",
                    value.getClass().getName(), key, Thread.currentThread().getName());
        }
    }

    /**
     * Unbinds the value for the given key from the current thread.
     *
     * @param key The key identifying the value bound to the current thread.
     * @return the object unbound or {@code null} if there was nothing bound under the specified key.
     */
    public static Object remove(Object key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        Object value = Objects.nonNull(map) ? map.remove(key) : null;
        if (Objects.nonNull(value) && log.isTraceEnabled()) {
            log.trace("Removed value of type [{}] for key [{}] from thread [{}]",
                    value.getClass().getName(), key, Thread.currentThread().getName());
        }
        return value;
    }

    // ========================= String Key API =========================

    public static <T> T get(String key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.isNull(map) ? null : (T) map.get(key);
    }

    public static <T> T get(String key, T defaultValue) {
        Object o = get(key);
        return Objects.nonNull(o) ? (T) o : defaultValue;
    }

    public Map<Object, Object> getValues() {
        return THREAD_LOCAL_POOL.get();
    }

    public void setValues(Map<Object, Object> values) {
        THREAD_LOCAL_POOL.set(values);
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
        if (value instanceof String && io.ddd4j.kit.lang.StrKit.isEmpty(((String) value))) {
            return;
        }
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        if (Objects.isNull(map)) {
            map = new ConcurrentHashMap<>(4);
        }
        map.put(key, value);
        THREAD_LOCAL_POOL.set(map);
    }

    public boolean contains(String key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.nonNull(map) && map.containsKey(key);
    }

    public static void remove(String key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        if (Objects.nonNull(map)) {
            map.remove(key);
            if (map.isEmpty()) {
                clear();
            }
        }
    }

    // ========================= Subject API =========================

    /**
     * Convenience method that simplifies retrieval of a thread-bound Subject.
     * If there is no Subject bound to the thread, this method returns {@code null}.
     *
     * @return the Subject object bound to the thread, or {@code null} if there isn't one bound.
     */
    public static Subject getSubject() {
        return get(SUBJECT_KEY);
    }

    /**
     * Convenience method that simplifies binding a Subject to the ThreadContext.
     *
     * @param subject the Subject object to bind to the thread. If the argument is null, nothing will be done.
     */
    public static void bind(io.ddd4j.core.subject.Subject subject) {
        if (Objects.nonNull(subject)) {
            put(SUBJECT_KEY, subject);
        }
    }

    /**
     * Convenience method that simplifies removal of a thread-local Subject from the thread.
     *
     * @return the Subject object previously bound to the thread, or {@code null} if there was none bound.
     */
    public static io.ddd4j.core.subject.Subject unbindSubject() {
        return (io.ddd4j.core.subject.Subject) remove((Object) SUBJECT_KEY);
    }

    // ========================= Lifecycle =========================

    /**
     * Removes the underlying ThreadLocal from the thread.
     * This method is meant to be the final 'clean up' operation that is called at the end of thread execution.
     */
    public static void clear() {
        THREAD_LOCAL_POOL.remove();
    }

    private static void ensureResourcesInitialized() {
        if (Objects.isNull(THREAD_LOCAL_POOL.get())) {
            THREAD_LOCAL_POOL.set(new ConcurrentHashMap<>(4));
        }
    }
}
