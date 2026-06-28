package io.ddd4j.core.cache;

import java.util.function.Function;

/**
 * 缓存管理器接口（纯 Java SPI，零框架依赖）。
 *
 * <p>负责创建和管理缓存实例。各框架适配层提供实现：
 * <ul>
 *   <li>{@code JetCacheCacheManager}（ddd4j-cache-core）— 基于 JetCache 统一引擎</li>
 *   <li>{@code SpringCacheManager} — 基于 Spring Cache 抽象</li>
 *   <li>自定义实现 — 直接实现本接口</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   CacheConfig config = CacheConfig.builder("user")
 *       .expireAfterWriteSeconds(300)
 *       .maximumSize(10000)
 *       .cacheType(CacheType.BOTH)
 *       .build();
 *
 *   // 创建或获取缓存
 *   Cache<String, User> cache = cacheManager.getOrCreateCache("user", config);
 *
 *   // 操作缓存
 *   cache.put("123", user);
 *   User cached = cache.getIfPresent("123");
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface CacheManager {

    /**
     * 创建或获取缓存实例。
     *
     * <p>如果指定名称的缓存已存在，则返回已有实例（忽略 config 参数）；
     * 如果不存在，则根据 config 创建新实例并注册。
     *
     * @param name   缓存名称（业务标识）
     * @param config 缓存配置
     * @param <K>    缓存键类型
     * @param <V>    缓存值类型
     * @return 缓存实例
     */
    <K, V> Cache<K, V> getOrCreateCache(String name, CacheConfig config);

    /**
     * 获取已注册的缓存实例（不创建）。
     *
     * @param name 缓存名称
     * @param <K>  缓存键类型
     * @param <V>  缓存值类型
     * @return 缓存实例，不存在时返回 null
     */
    <K, V> Cache<K, V> getCache(String name);

    /**
     * 获取已注册缓存的所有名称。
     *
     * @return 缓存名称集合
     */
    java.util.Set<String> getCacheNames();

    /**
     * 创建带加载器的自动加载缓存。
     *
     * <p>当缓存未命中时，自动调用 loader 加载值并缓存。
     *
     * @param name   缓存名称
     * @param config 缓存配置
     * @param loader 缓存加载器
     * @param <K>    缓存键类型
     * @param <V>    缓存值类型
     * @return 自动加载缓存实例
     */
    default <K, V> Cache<K, V> getOrCreateLoadingCache(String name, CacheConfig config, Function<K, V> loader) {
        // 默认实现：创建普通缓存，get(key, loader) 时按需加载
        // 支持自动加载的实现（如 Caffeine/JetCache）应覆盖此方法提供真正的预加载能力
        return getOrCreateCache(name, config);
    }

}
