package io.ddd4j.kit.cache;

import io.ddd4j.kit.cache.impl.CaffeineCache;
import io.ddd4j.kit.cache.impl.GuavaCache;
import io.ddd4j.kit.cache.impl.HutoolCache;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 统一缓存工具门面
 *
 * <p>提供统一的缓存操作抽象，支持多种缓存后端：
 * <ul>
 *   <li>{@link CacheType#CAFFEINE} - Caffeine 本地缓存（默认，性能最优）</li>
 *   <li>{@link CacheType#GUAVA} - Guava 本地缓存</li>
 *   <li>{@link CacheType#HUTOOL} - Hutool 本地缓存</li>
 * </ul>
 *
 * <p>每个业务可以独立配置缓存参数（过期时间、最大容量、刷新策略等），
 * 参考 {@code AgentPromptAppService} 的配置方式。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 1. 简单缓存
 *   CacheKit.build("user", 300);
 *   CacheKit.put("user", "123", userObject);
 *   User user = CacheKit.get("user", "123");
 *
 *   // 2. 详细配置
 *   CacheKit.build("user", builder -> builder
 *       .expireAfterWrite(300)
 *       .expireAfterAccess(600)
 *       .maximumSize(1000)
 *       .recordStats()
 *       .removalListener(key -> log.info("Removed: {}", key))
 *   );
 *
 *   // 3. 自动加载缓存
 *   CacheKit.buildWithLoader("config", key -> loadFromDB(key), 60, 30);
 *   String config = CacheKit.getWithLoader("config", "app.name");
 *
 *   // 4. 切换缓存后端
 *   CacheKit.setDefaultType(CacheType.GUAVA);
 *   CacheKit.build("user", 300);  // 使用 Guava
 * }</pre>
 *
 * @author wandl
 * @since 2.0.x
 */
@UtilityClass
@Slf4j
public class CacheKit {

    /**
     * 默认缓存类型（Caffeine）
     */
    private CacheType defaultType = CacheType.CAFFEINE;

    /**
     * 普通缓存实例（key 为业务标识 biz）
     */
    private final Map<String, Cache<String, Object>> CACHES = new ConcurrentHashMap<>();

    /**
     * 自动加载缓存实例（key 为业务标识 biz）
     */
    private final Map<String, Cache<String, Object>> LOADING_CACHES = new ConcurrentHashMap<>();

    // ==================== 全局配置 ====================

    /**
     * 设置默认缓存类型
     *
     * @param type 缓存类型（CAFFEINE/GUAVA/HUTOOL）
     */
    public void setDefaultType(CacheType type) {
        CacheKit.defaultType = type;
        log.info("默认缓存类型已设置为: {}", type);
    }

    /**
     * 获取当前默认缓存类型
     *
     * @return 默认缓存类型
     */
    public CacheType getDefaultType() {
        return defaultType;
    }

    // ==================== 简单缓存 ====================

    /**
     * 构建缓存（使用默认缓存类型，写后过期策略）
     *
     * @param biz            业务标识
     * @param expiredSeconds 过期时间（秒）
     */
    public void build(String biz, long expiredSeconds) {
        build(biz, expiredSeconds, defaultType);
    }

    /**
     * 构建缓存（指定缓存类型，写后过期策略）
     *
     * @param biz            业务标识
     * @param expiredSeconds 过期时间（秒）
     * @param type           缓存类型
     */
    public void build(String biz, long expiredSeconds, CacheType type) {
        CacheConfigBuilder config = new CacheConfigBuilder().expireAfterWrite(expiredSeconds);
        Cache<String, Object> cache = createCache(type, config);
        CACHES.put(biz, cache);
        log.info("已构建缓存: biz={}, type={}, 过期时间={}秒", biz, type, expiredSeconds);
    }

    // ==================== 详细配置缓存（Builder 模式） ====================

    /**
     * 构建缓存（使用默认缓存类型，Builder 模式配置）
     *
     * <p>支持每个业务独立配置缓存参数：
     * <pre>{@code
     *   CacheKit.build("user", builder -> builder
     *       .expireAfterWrite(300)
     *       .expireAfterAccess(600)
     *       .maximumSize(1000)
     *       .recordStats()
     *       .removalListener(key -> log.info("Removed: {}", key))
     *   );
     * }</pre>
     *
     * @param biz     业务标识
     * @param builder 配置构建器函数
     */
    public void build(String biz, Function<CacheConfigBuilder, CacheConfigBuilder> builder) {
        build(biz, builder, defaultType);
    }

    /**
     * 构建缓存（指定缓存类型，Builder 模式配置）
     *
     * @param biz     业务标识
     * @param builder 配置构建器函数
     * @param type    缓存类型
     */
    public void build(String biz, Function<CacheConfigBuilder, CacheConfigBuilder> builder, CacheType type) {
        CacheConfigBuilder config = builder.apply(new CacheConfigBuilder());
        Cache<String, Object> cache = createCache(type, config);
        CACHES.put(biz, cache);
        log.info("已构建缓存: biz={}, type={}", biz, type);
    }

    // ==================== 自动加载缓存 ====================

    /**
     * 构建自动加载缓存（简单配置，写后过期）
     *
     * @param biz            业务标识
     * @param loader         缓存加载器（当缓存未命中时调用）
     * @param expiredSeconds 过期时间（秒）
     */
    public void buildWithLoader(String biz, Function<String, Object> loader, long expiredSeconds) {
        buildWithLoader(biz, loader, expiredSeconds, 0);
    }

    /**
     * 构建自动加载缓存（带自动刷新）
     *
     * @param biz             业务标识
     * @param loader          缓存加载器
     * @param expiredSeconds  过期时间（秒）
     * @param refreshSeconds  刷新时间（秒，0 表示不自动刷新）
     */
    public void buildWithLoader(String biz, Function<String, Object> loader,
                                long expiredSeconds, long refreshSeconds) {
        buildWithLoader(biz, builder -> builder
                .expireAfterWrite(expiredSeconds)
                .refreshAfterWrite(refreshSeconds)
                .recordStats()
        , loader);
    }

    /**
     * 构建自动加载缓存（Builder 模式配置）
     *
     * <p>支持每个业务独立配置缓存参数：
     * <pre>{@code
     *   CacheKit.buildWithLoader("prompt", builder -> builder
     *       .expireAfterWrite(300)
     *       .refreshAfterWrite(60)
     *       .expireAfterAccess(600)
     *       .maximumSize(1000)
     *       .recordStats()
     *       .removalListener(key -> log.info("Removed: {}", key))
     *   , dataId -> loadPromptFromNacos(dataId));
     * }</pre>
     *
     * @param biz     业务标识
     * @param builder 配置构建器函数
     * @param loader  缓存加载器
     */
    public void buildWithLoader(String biz, Function<CacheConfigBuilder, CacheConfigBuilder> builder,
                                Function<String, Object> loader) {
        CacheConfigBuilder config = builder.apply(new CacheConfigBuilder());
        Cache<String, Object> cache = CaffeineCache.createLoadingCache(config, loader::apply);
        LOADING_CACHES.put(biz, cache);
        log.info("已构建自动加载缓存: biz={}", biz);
    }

    // ==================== 缓存操作 ====================

    /**
     * 获取缓存值
     *
     * @param biz 业务标识
     * @param key 缓存键
     * @param <T> 值类型
     * @return 缓存值，如果不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String biz, String key) {
        Cache<String, Object> cache = CACHES.get(biz);
        if (cache == null) {
            return null;
        }
        return (T) cache.getIfPresent(key);
    }

    /**
     * 获取缓存值（如果不存在则通过 mappingFunction 加载）
     *
     * @param biz             业务标识
     * @param key             缓存键
     * @param mappingFunction 值加载函数
     * @param <T>             值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String biz, String key, Function<String, Object> mappingFunction) {
        Cache<String, Object> cache = CACHES.get(biz);
        if (cache == null) {
            return null;
        }
        return (T) cache.get(key, mappingFunction);
    }

    /**
     * 设置缓存值
     *
     * @param biz   业务标识
     * @param key   缓存键
     * @param value 缓存值
     */
    public void put(String biz, String key, Object value) {
        Cache<String, Object> cache = CACHES.get(biz);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    /**
     * 删除指定缓存项
     *
     * @param biz 业务标识
     * @param key 缓存键
     */
    public void invalidate(String biz, String key) {
        Cache<String, Object> cache = CACHES.get(biz);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    /**
     * 清空指定业务的所有缓存
     *
     * @param biz 业务标识
     */
    public void invalidateAll(String biz) {
        Cache<String, Object> cache = CACHES.get(biz);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /**
     * 判断缓存是否存在
     *
     * @param biz 业务标识
     * @param key 缓存键
     * @return true 表示存在，false 表示不存在
     */
    public boolean exists(String biz, String key) {
        Cache<String, Object> cache = CACHES.get(biz);
        return cache != null && cache.getIfPresent(key) != null;
    }

    /**
     * 获取缓存统计信息
     *
     * @param biz 业务标识
     * @return 缓存统计信息（命中率、命中次数、未命中次数等）
     */
    public CacheStats getStats(String biz) {
        Cache<String, Object> cache = CACHES.get(biz);
        if (cache != null) {
            return cache.stats();
        }
        return null;
    }

    /**
     * 获取原生缓存实例
     *
     * @param biz 业务标识
     * @return 缓存实例，如果不存在返回 null
     */
    public Cache<String, Object> getCache(String biz) {
        return CACHES.get(biz);
    }

    // ==================== 自动加载缓存操作 ====================

    /**
     * 获取自动加载缓存值（如果不存在则通过 CacheLoader 加载）
     *
     * @param biz 业务标识
     * @param key 缓存键
     * @param <T> 值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithLoader(String biz, String key) {
        Cache<String, Object> cache = LOADING_CACHES.get(biz);
        if (cache == null) {
            return null;
        }
        try {
            return (T) cache.get(key);
        } catch (Exception e) {
            log.error("从自动加载缓存获取失败: biz={}, key={}", biz, key, e);
            return null;
        }
    }

    /**
     * 刷新自动加载缓存（异步刷新缓存值）
     *
     * @param biz 业务标识
     * @param key 缓存键
     */
    public void refresh(String biz, String key) {
        Cache<String, Object> cache = LOADING_CACHES.get(biz);
        if (cache != null) {
            cache.refresh(key);
        }
    }

    /**
     * 删除自动加载缓存项
     *
     * @param biz 业务标识
     * @param key 缓存键
     */
    public void invalidateLoader(String biz, String key) {
        Cache<String, Object> cache = LOADING_CACHES.get(biz);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    /**
     * 清空指定业务的所有自动加载缓存
     *
     * @param biz 业务标识
     */
    public void invalidateAllLoader(String biz) {
        Cache<String, Object> cache = LOADING_CACHES.get(biz);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /**
     * 获取自动加载缓存统计信息
     *
     * @param biz 业务标识
     * @return 缓存统计信息
     */
    public CacheStats getLoaderStats(String biz) {
        Cache<String, Object> cache = LOADING_CACHES.get(biz);
        if (cache != null) {
            return cache.stats();
        }
        return null;
    }

    // ==================== 内部方法 ====================

    /**
     * 根据缓存类型和配置创建缓存实例（内部方法）
     *
     * @param type   缓存类型
     * @param config 缓存配置
     * @return 缓存实例
     */
    private Cache<String, Object> createCache(CacheType type, CacheConfigBuilder config) {
        switch (type) {
            case CAFFEINE:
                return CaffeineCache.createCache(config);
            case GUAVA:
                return GuavaCache.createCache(config);
            case HUTOOL:
                return HutoolCache.createCache(config);
            default:
                throw new IllegalArgumentException("不支持的缓存类型: " + type);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 缓存类型枚举
     */
    public enum CacheType {
        /**
         * Caffeine 本地缓存（默认，性能最优）
         */
        CAFFEINE,
        /**
         * Guava 本地缓存（稳定可靠）
         */
        GUAVA,
        /**
         * Hutool 本地缓存（轻量级）
         */
        HUTOOL
    }

    /**
     * 缓存配置构建器
     *
     * <p>用于配置缓存的各种参数，参考 {@code AgentPromptAppService} 的配置方式。
     */
    public static class CacheConfigBuilder {
        /**
         * 最大容量
         */
        private long maximumSize = 1000;
        /**
         * 写后过期时间（秒）
         */
        private long expireAfterWriteSeconds = 0;
        /**
         * 访问后过期时间（秒）
         */
        private long expireAfterAccessSeconds = 0;
        /**
         * 写后刷新时间（秒）
         */
        private long refreshAfterWriteSeconds = 0;
        /**
         * 初始容量
         */
        private int initialCapacity = 0;
        /**
         * 是否记录统计信息
         */
        private boolean recordStats = false;
        /**
         * 移除监听器
         */
        private Consumer<String> removalListener;

        /**
         * 设置最大容量
         */
        public CacheConfigBuilder maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * 设置写后过期时间
         */
        public CacheConfigBuilder expireAfterWrite(long seconds) {
            this.expireAfterWriteSeconds = seconds;
            return this;
        }

        /**
         * 设置访问后过期时间
         */
        public CacheConfigBuilder expireAfterAccess(long seconds) {
            this.expireAfterAccessSeconds = seconds;
            return this;
        }

        /**
         * 设置写后刷新时间
         */
        public CacheConfigBuilder refreshAfterWrite(long seconds) {
            this.refreshAfterWriteSeconds = seconds;
            return this;
        }

        /**
         * 设置初始容量
         */
        public CacheConfigBuilder initialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        /**
         * 启用统计信息记录
         */
        public CacheConfigBuilder recordStats() {
            this.recordStats = true;
            return this;
        }

        /**
         * 设置移除监听器
         */
        public CacheConfigBuilder removalListener(Consumer<String> listener) {
            this.removalListener = listener;
            return this;
        }

        // ========== Getter 方法，供实现类使用 ==========

        public long getMaximumSize() { return maximumSize; }
        public long getExpireAfterWriteSeconds() { return expireAfterWriteSeconds; }
        public long getExpireAfterAccessSeconds() { return expireAfterAccessSeconds; }
        public long getRefreshAfterWriteSeconds() { return refreshAfterWriteSeconds; }
        public int getInitialCapacity() { return initialCapacity; }
        public boolean isRecordStats() { return recordStats; }
        public Consumer<String> getRemovalListener() { return removalListener; }
    }

}
