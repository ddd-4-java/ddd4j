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
package io.ddd4j.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.subject.Subject;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可透传的线程上下文容器。
 *
 * <p>上下文通过 {@link TransmittableThreadLocal} 在线程池任务间透传。透传时会复制资源 Map，
 * 避免父线程和异步任务共享同一个可变容器；Map 中的业务对象保持引用语义，不进行深拷贝。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
@SuppressWarnings("unchecked")
public class ThreadContext {

    public static final String SECURITY_MANAGER_KEY = ThreadContext.class.getName() + "_SECURITY_MANAGER_KEY";
    public static final String SUBJECT_KEY = ThreadContext.class.getName() + "_SUBJECT_KEY";

    /**
     * 线程变量池
     */
    private static final TransmittableThreadLocal<Map<Object, Object>> THREAD_LOCAL_POOL =
            new TransmittableThreadLocal<>() {
                @Override
                public Map<Object, Object> copy(Map<Object, Object> parentValue) {
                    return copyResources(parentValue);
                }
            };

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
        replaceResources(newResources);
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
        if (Objects.nonNull(map) && map.isEmpty()) {
            clear();
        }
        return value;
    }

    // ========================= String Key API =========================

    /**
     * 获取当前线程绑定中指定 key 的值。
     *
     * @param key 键
     * @param <T> 值类型
     * @return 对应的值，不存在返回 null
     */
    public static <T> T get(String key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.isNull(map) ? null : (T) map.get(key);
    }

    /**
     * 获取当前线程绑定中指定 key 的值，不存在返回默认值。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 对应的值，不存在返回 defaultValue
     */
    public static <T> T get(String key, T defaultValue) {
        Object o = get(key);
        return Objects.nonNull(o) ? (T) o : defaultValue;
    }

    /**
     * 获取当前线程绑定的全部键值对。
     */
    public static Map<Object, Object> getValues() {
        return getResources();
    }

    /**
     * 设置当前线程绑定的全部键值对。
     */
    public static void setValues(Map<Object, Object> values) {
        setResources(values);
    }

    /**
     * 条件设置：仅当 condition 为 true 时绑定值到当前线程。
     *
     * @param condition 执行条件
     * @param key       键
     * @param value     值
     */
    public static void set(boolean condition, String key, Object value) {
        if (!condition) {
            return;
        }
        set(key, value);
    }

    /**
     * 绑定字符串键值对到当前线程。
     *
     * @param key   键
     * @param value 值（null 或空字符串不处理）
     */
    public static void set(String key, Object value) {
        if (Objects.isNull(value)) {
            return;
        }
        if (value instanceof String && io.ddd4j.kit.lang.StrKit.isEmpty(((String) value))) {
            return;
        }
        put(key, value);
    }

    /**
     * 判断当前线程是否包含指定 key。
     *
     * @param key 键
     * @return true 表示包含
     */
    public static boolean contains(String key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        return Objects.nonNull(map) && map.containsKey(key);
    }

    /**
     * 从当前线程移除指定 key 的绑定。
     *
     * @param key 键
     */
    public static void remove(String key) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        if (Objects.nonNull(map)) {
            map.remove(key);
            if (map.isEmpty()) {
                clear();
            }
        }
    }

    // ========================= SPI Service Lookup =========================

    /**
     * 按 SPI 约定 key 在当前线程上下文中查找指定类型的服务实例（类型安全）。
     * <p>
     * 与 {@link BaseContext#get(String, Class)} 配合使用：
     * <ul>
     *   <li>{@code ThreadContext}（本方法）：线程级，请求级 SPI 可覆盖全局默认</li>
     *   <li>{@code BaseContext}：JVM 级，全局默认 SPI</li>
     * </ul>
     * 业务代码通常应使用 {@link io.ddd4j.core.context.Contexts#get} 统一查找（线程优先 → 全局兜底）。
     *
     * @param key  SPI 约定 key（参见 {@link SpiKeys}）
     * @param type 期望的服务类型
     * @param <T>  服务类型
     * @return 包装的服务实例 Optional，未找到或类型不匹配返回 {@link Optional#empty()}
     */
    public static <T> Optional<T> get(String key, Class<T> type) {
        Map<Object, Object> map = THREAD_LOCAL_POOL.get();
        if (Objects.isNull(map)) {
            return Optional.empty();
        }
        Object value = map.get(key);
        if (Objects.isNull(value)) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    /**
     * 按 SPI 约定 key 在当前线程上下文中注册类型安全的服务实例。
     * <p>
     * 常用于请求拦截器为当前线程注入租户/语言特定的 SPI 覆盖。
     *
     * @param key   SPI 约定 key（参见 {@link SpiKeys}）
     * @param type  期望的服务类型
     * @param value 服务实例
     * @param <T>   服务类型
     */
    public static <T> void inject(String key, Class<T> type, T value) {
        if (Objects.isNull(key) || Objects.isNull(value)) {
            throw new IllegalArgumentException("SPI key and service cannot be null");
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "SPI service must be instance of " + type.getName() + ", but was " + value.getClass().getName());
        }
        ensureResourcesInitialized();
        THREAD_LOCAL_POOL.get().put(key, value);
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
     * 创建可自动恢复的上下文作用域。
     *
     * <p>作用域内可使用现有 API 修改上下文，关闭后恢复进入作用域前的资源快照。</p>
     *
     * @return 上下文作用域
     */
    public static Scope open() {
        return new Scope(snapshotResources());
    }

    /**
     * 使用指定资源创建可自动恢复的上下文作用域。
     *
     * @param resources 当前作用域使用的资源；为 null 或空 Map 时清空当前上下文
     * @return 上下文作用域
     */
    public static Scope open(Map<Object, Object> resources) {
        Map<Object, Object> previousResources = snapshotResources();
        replaceResources(resources);
        return new Scope(previousResources);
    }

    /**
     * Removes the underlying ThreadLocal from the thread.
     * This method is meant to be the final 'clean up' operation that is called at the end of thread execution.
     */
    public static void clear() {
        THREAD_LOCAL_POOL.remove();
    }

    public static final class Scope implements AutoCloseable {

        private final Map<Object, Object> previousResources;
        private boolean closed;

        private Scope(Map<Object, Object> previousResources) {
            this.previousResources = previousResources;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            replaceResources(previousResources);
            closed = true;
        }
    }

    private static void ensureResourcesInitialized() {
        if (Objects.isNull(THREAD_LOCAL_POOL.get())) {
            THREAD_LOCAL_POOL.set(new ConcurrentHashMap<>(4));
        }
    }

    private static Map<Object, Object> snapshotResources() {
        return copyResources(THREAD_LOCAL_POOL.get());
    }

    private static Map<Object, Object> copyResources(Map<Object, Object> resources) {
        return Objects.isNull(resources) ? null : new ConcurrentHashMap<>(resources);
    }

    private static void replaceResources(Map<Object, Object> resources) {
        if (Objects.isNull(resources) || resources.isEmpty()) {
            clear();
            return;
        }
        THREAD_LOCAL_POOL.set(copyResources(resources));
    }
}
