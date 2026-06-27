package io.ddd4j.core.context;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局基础上下文容器（纯 Java，零框架依赖）。
 * <p>
 * 提供一个 JVM 级别的静态键值存储，用于在不同模块之间共享全局上下文信息。
 * 底层使用 {@link ConcurrentHashMap} 保证线程安全。
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>应用启动时注入基础配置（如 {@code PROJECT_PACKAGE}、{@code APPLICATION_NAME}）</li>
 *   <li>跨模块共享单例对象（如全局配置、序列化器实例）</li>
 *   <li>运行时动态注册/查询元数据</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注入
 * BaseContext.inject("APP_NAME", "my-service");
 *
 * // 查询
 * String appName = BaseContext.get("APP_NAME");
 *
 * // 判断是否存在
 * if (BaseContext.contains("APP_NAME")) { ... }
 * }</pre>
 *
 * <h3>与 {@link ThreadContext} 的区别</h3>
 * <ul>
 *   <li>{@code BaseContext} — JVM 全局，所有线程共享，适合存储不随请求变化的静态配置</li>
 *   <li>{@code ThreadContext} — 线程隔离，每个线程独立副本，适合存储请求级上下文（如租户ID、用户ID）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ThreadContext 线程级上下文
 * @see I18nKit 国际化上下文
 * @since 3.4.x
 */
@UtilityClass
public class BaseContext {

    /**
     * 全局键值存储（线程安全）。
     * <p>
     * 使用 {@link ConcurrentHashMap} 保证并发读写安全。
     * 键和值均为 Object 类型，支持任意类型的上下文数据。
     */
    private static final Map<Object, Object> GLOBAL = new ConcurrentHashMap<>();

    /**
     * 向全局上下文注入一个键值对。
     * <p>
     * 如果 key 已存在，旧值将被覆盖。
     *
     * @param key   键（不能为 {@code null}）
     * @param value 值（可以为 {@code null}，但不建议）
     * @param <K>   键类型
     * @param <V>   值类型
     */
    public <K, V> void inject(K key, V value) {
        GLOBAL.put(key, value);
    }

    /**
     * 从全局上下文获取指定 key 对应的值。
     * <p>
     * 返回值通过泛型强制转型，调用方需确保类型安全。
     * 如果 key 不存在，返回 {@code null}。
     *
     * @param key  键
     * @param <K>  键类型
     * @param <V>  期望的值类型
     * @return 对应的值，不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <K, V> V get(K key) {
        return (V) GLOBAL.get(key);
    }

    /**
     * 判断全局上下文中是否包含指定 key。
     *
     * @param key 键
     * @param <K> 键类型
     * @return {@code true} 表示存在，{@code false} 表示不存在
     */
    public <K> boolean contains(K key) {
        return GLOBAL.containsKey(key);
    }

}
