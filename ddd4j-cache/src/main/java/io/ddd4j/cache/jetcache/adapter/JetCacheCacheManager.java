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
package io.ddd4j.cache.jetcache.adapter;

import com.alicp.jetcache.template.QuickConfig;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 基于 JetCache 的缓存管理器实现。
 *
 * <p>实现 ddd4j {@link CacheManager} SPI，内部委托 JetCache 的
 * {@link com.alicp.jetcache.CacheManager} 创建和管理缓存实例。
 *
 * <p>创建的缓存实例通过 {@link JetCacheAdapter} 适配为 ddd4j {@link Cache} 接口返回。
 * 业务代码面向 ddd4j Cache 接口编程，不直接接触 JetCache API。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 1. 创建 CacheManager（由框架适配层注入，如 Spring AutoConfig）
 *   JetCacheCacheManager manager = new JetCacheCacheManager(jetCacheCacheManager);
 *
 *   // 2. 创建或获取缓存
 *   CacheConfig config = CacheConfig.builder("user")
 *       .expireAfterWriteSeconds(300)
 *       .cacheType(CacheType.BOTH)
 *       .build();
 *   Cache<String, User> cache = manager.getOrCreateCache("user", config);
 *
 *   // 3. 操作缓存（面向 ddd4j Cache 接口）
 *   cache.put("123", user);
 *   User cached = cache.getIfPresent("123");
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@SuppressWarnings("unchecked")
public class JetCacheCacheManager implements CacheManager {

    /**
     * 默认 JetCache 区域名称
     */
    public static final String DEFAULT_AREA = "default";
    private final com.alicp.jetcache.CacheManager jetCacheManager;
    private final String cacheArea;

    /**
     * 已创建的缓存实例（name → ddd4j Cache 适配器）
     */
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    /**
     * 构造 JetCache 缓存管理器（使用默认区域 "default"）。
     *
     * @param jetCacheManager JetCache CacheManager 实例
     */
    public JetCacheCacheManager(com.alicp.jetcache.CacheManager jetCacheManager) {
        this(jetCacheManager, DEFAULT_AREA);
    }

    /**
     * 构造 JetCache 缓存管理器（指定区域）。
     *
     * @param jetCacheManager JetCache CacheManager 实例
     * @param cacheArea       JetCache 区域名称
     */
    public JetCacheCacheManager(com.alicp.jetcache.CacheManager jetCacheManager, String cacheArea) {
        Objects.requireNonNull(jetCacheManager, "jetCacheManager 不能为空");
        this.jetCacheManager = jetCacheManager;
        this.cacheArea = Objects.nonNull(cacheArea) ? cacheArea : DEFAULT_AREA;
    }

    @Override
    public <K, V> Cache<K, V> getOrCreateCache(String name, CacheConfig config) {
        return (Cache<K, V>) caches.computeIfAbsent(name, key -> {
            QuickConfig quickConfig = io.ddd4j.cache.jetcache.config.CacheConfigConverter.toQuickConfig(config, cacheArea);
            com.alicp.jetcache.Cache<K, V> jetCache = jetCacheManager.getOrCreateCache(quickConfig);
            log.info("已创建 JetCache 缓存: name={}, area={}, type={}", key, cacheArea, config.getCacheType());
            return new JetCacheAdapter<>(jetCache);
        });
    }

    @Override
    public <K, V> Cache<K, V> getCache(String name) {
        return (Cache<K, V>) caches.get(name);
    }

    @Override
    public Set<String> getCacheNames() {
        return caches.keySet();
    }

    @Override
    public <K, V> Cache<K, V> getOrCreateLoadingCache(String name, CacheConfig config, Function<K, V> loader) {
        return (Cache<K, V>) caches.computeIfAbsent(name, key -> {
            QuickConfig quickConfig = io.ddd4j.cache.jetcache.config.CacheConfigConverter.toQuickConfig(config, cacheArea);
            com.alicp.jetcache.Cache<K, V> jetCache = jetCacheManager.getOrCreateCache(quickConfig);
            // JetCache 通过 computeIfAbsent 内置加载器语义，无需额外配置 CacheLoader
            log.info("已创建 JetCache 加载缓存: name={}, area={}", key, cacheArea);
            return new JetCacheAdapter<>(jetCache);
        });
    }

}
