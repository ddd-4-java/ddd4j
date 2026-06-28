package io.ddd4j.core.cache;

import java.util.Map;
import java.util.function.Function;

/**
 * 缓存统一接口（纯 Java，零框架依赖）。
 *
 * <p>提供统一的缓存操作抽象，支持多种缓存后端实现（Caffeine、Guava、Hutool、Redis、Redisson 等）。
 * 接口设计与 Caffeine 保持一致，方便业务开发中可以改变实现。
 *
 * <p>各框架适配层（ddd4j-cache-core 等）提供具体实现：
 * <ul>
 *   <li>{@code JetCacheAdapter} — 基于 JetCache 统一引擎的适配</li>
 *   <li>{@code CaffeineCache} / {@code GuavaCache} / {@code HutoolCache} — 本地缓存实现</li>
 *   <li>{@code RedisCache} / {@code RedissonCache} — 分布式缓存实现</li>
 * </ul>
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #getIfPresent(Object)} - 获取缓存值，如果不存在返回 null</li>
 *   <li>{@link #get(Object)} - 获取缓存值（自动加载缓存专用）</li>
 *   <li>{@link #get(Object, Function)} - 获取缓存值，如果不存在则通过函数加载</li>
 *   <li>{@link #put(Object, Object)} - 设置缓存值</li>
 *   <li>{@link #invalidate(Object)} - 删除缓存</li>
 *   <li>{@link #refresh(Object)} - 刷新缓存（自动加载缓存专用）</li>
 *   <li>{@link #invalidateAll()} - 清空所有缓存</li>
 *   <li>{@link #estimatedSize()} - 获取缓存大小</li>
 *   <li>{@link #stats()} - 获取统计信息</li>
 * </ul>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface Cache<K, V> {

    /**
     * 获取缓存值，如果不存在返回 null。
     *
     * @param key 缓存键
     * @return 缓存值，如果不存在返回 null
     */
    V getIfPresent(K key);

    /**
     * 获取缓存值（自动加载缓存专用）。
     *
     * <p>如果缓存中不存在指定键的值，则通过 CacheLoader 自动加载。
     * 普通缓存实现此方法时应返回 {@link #getIfPresent(Object)} 的结果，
     * 自动加载缓存实现时应调用 CacheLoader 加载值。
     *
     * @param key 缓存键
     * @return 缓存值
     */
    default V get(K key) {
        return getIfPresent(key);
    }

    /**
     * 获取缓存值，如果不存在则通过函数加载。
     *
     * <p>如果缓存中不存在指定键的值，则调用 mappingFunction 计算值，
     * 并将计算结果存入缓存后返回。
     *
     * @param key             缓存键
     * @param mappingFunction 值加载函数
     * @return 缓存值
     */
    V get(K key, Function<K, V> mappingFunction);

    /**
     * 设置缓存值。
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void put(K key, V value);

    /**
     * 批量设置缓存值。
     *
     * @param map 缓存键值对
     */
    default void putAll(Map<K, V> map) {
        map.forEach(this::put);
    }

    /**
     * 删除缓存。
     *
     * @param key 缓存键
     */
    void invalidate(K key);

    /**
     * 刷新缓存（自动加载缓存专用）。
     *
     * <p>刷新指定键的缓存值，下次访问时会重新加载。
     * 普通缓存实现此方法时应调用 {@link #invalidate(Object)}。
     *
     * @param key 缓存键
     */
    default void refresh(K key) {
        invalidate(key);
    }

    /**
     * 批量删除缓存。
     *
     * @param keys 缓存键集合
     */
    default void invalidateAll(Iterable<K> keys) {
        keys.forEach(this::invalidate);
    }

    /**
     * 清空所有缓存。
     */
    void invalidateAll();

    /**
     * 获取缓存大小（估算值）。
     *
     * @return 缓存条目数
     */
    long estimatedSize();

    /**
     * 获取缓存统计信息。
     *
     * @return 缓存统计信息，如果不支持返回 null
     */
    CacheStats stats();

    // ==================== TTL 管理（default 方法，不支持时抛 UnsupportedOperationException） ====================

    /**
     * 设置 key 的过期时间。
     *
     * <p>仅远程缓存（Redis/Memcached）和部分本地缓存（Caffeine）支持。
     *
     * @param key     缓存键
     * @param seconds 过期时间（秒）
     * @return true 表示设置成功
     */
    default boolean expire(K key, long seconds) {
        throw new UnsupportedOperationException("此缓存实现不支持 expire");
    }

    /**
     * 获取 key 的剩余过期时间。
     *
     * @param key 缓存键
     * @return 剩余秒数（-1 表示永久有效，-2 表示 key 不存在，-3 表示不支持）
     */
    default long getExpire(K key) {
        return -3L;
    }

    /**
     * 移除 key 的过期时间，使其永久有效。
     *
     * @param key 缓存键
     * @return true 表示设置成功
     */
    default boolean persist(K key) {
        throw new UnsupportedOperationException("此缓存实现不支持 persist");
    }

}
