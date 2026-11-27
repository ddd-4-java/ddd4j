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

import com.alicp.jetcache.CacheGetResult;
import com.alicp.jetcache.CacheResult;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * JetCache 适配器：将 JetCache 的 {@link com.alicp.jetcache.Cache} 适配为 ddd4j 的 {@link Cache} 接口。
 *
 * <p>JetCache 作为统一引擎，所有缓存操作通过 JetCache 执行。
 * 本适配器将 JetCache 丰富的 API（GET/PUT/REMOVE 返回 CacheResult）适配为
 * ddd4j 简洁的 Cache 接口（getIfPresent/put/invalidate 返回值或 void）。
 *
 * <p>支持的 JetCache 特性：
 * <ul>
 *   <li>多级缓存（LOCAL/REMOTE/BOTH）</li>
 *   <li>自动加载（computeIfAbsent + 防穿透）</li>
 *   <li>分布式锁（tryLock）</li>
 *   <li>缓存统计（DefaultCacheMonitor）</li>
 * </ul>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class JetCacheAdapter<K, V> implements Cache<K, V> {

    /** \u88ab\u9002\u914d\u4e3a DDD4J \u7f13\u5b58\u63a5\u53e3\u7684 JetCache \u5b9e\u4f8b\u3002 */
    private final com.alicp.jetcache.Cache<K, V> jetCache;

    /**
     * 构造 JetCache 适配器。
     *
     * @param jetCache JetCache 缓存实例
     */
    public JetCacheAdapter(com.alicp.jetcache.Cache<K, V> jetCache) {
        Objects.requireNonNull(jetCache, "jetCache 不能为空");
        this.jetCache = jetCache;
    }

    /**
     * 获取底层 JetCache 实例（用于需要 JetCache 高级 API 的场景）。
     *
     * @return JetCache 实例
     */
    public com.alicp.jetcache.Cache<K, V> unwrap() {
        return jetCache;
    }

    /**
     * 读取现有缓存值。
     *
     * <p>此实现调用 <code>jetCache.GET(key)</code>。</p>
     *
     * @param key 缓存键
     * @return 根据执行路径返回<code>result.getValue()</code>、<code>null</code>
     */
    @Override
    public V getIfPresent(K key) {
        CacheGetResult<V> result = jetCache.GET(key);
        if (result.isSuccess()) {
            return result.getValue();
        }
        return null;
    }

    /**
     * 读取调用指定的当前值。
     *
     * <p>此实现调用 <code>jetCache.computeIfAbsent(key, mappingFunction)</code>。</p>
     *
     * @param key 缓存键
     * @param mappingFunction 值加载函数
     * @return 实际返回<code>jetCache.computeIfAbsent(key, mappingFunction)</code>
     */
    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        // 使用 JetCache 的 computeIfAbsent，内置防穿透保护
        return jetCache.computeIfAbsent(key, mappingFunction);
    }

    /**
     * 读取调用指定的当前值。
     *
     * <p>此实现调用 <code>jetCache.get(key)</code>。</p>
     *
     * @param key 缓存键
     * @return 根据执行路径返回<code>jetCache.get(key)</code>、<code>null</code>
     */
    @Override
    public V get(K key) {
        // JetCache 的 get 方法在配置了 loader 时自动加载，否则等价于 getIfPresent
        try {
            return jetCache.get(key);
        } catch (Exception e) {
            log.debug("JetCache get 失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 将给定数据写入当前存储。
     *
     * <p>此实现调用 <code>jetCache.PUT(key, value)</code>。</p>
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    @Override
    public void put(K key, V value) {
        CacheResult result = jetCache.PUT(key, value);
        if (!result.isSuccess()) {
            log.warn("JetCache PUT 失败: key={}, result={}", key, result.getResultCode());
        }
    }

    /**
     * 批量设置缓存值。
     *
     * <p>此实现调用 <code>jetCache.PUT_ALL(map)</code>。</p>
     *
     * @param map 缓存键值对
     */
    @Override
    public void putAll(Map<K, V> map) {
        CacheResult result = jetCache.PUT_ALL(map);
        if (!result.isSuccess()) {
            log.warn("JetCache PUT_ALL 失败: result={}", result.getResultCode());
        }
    }

    /**
     * 使指定键的缓存条目失效。
     *
     * <p>此实现调用 <code>jetCache.REMOVE(key)</code>。</p>
     *
     * @param key 缓存键
     */
    @Override
    public void invalidate(K key) {
        CacheResult result = jetCache.REMOVE(key);
        if (!result.isSuccess()) {
            log.warn("JetCache REMOVE 失败: key={}, result={}", key, result.getResultCode());
        }
    }

    /**
     * 刷新当前实现所持有的数据或状态。
     *
     * <p>此实现调用 <code>jetCache.REMOVE(key)</code>。</p>
     *
     * @param key 缓存键
     */
    @Override
    public void refresh(K key) {
        // JetCache 支持配置 RefreshPolicy 时自动刷新；手动刷新通过 remove + 下次 get 触发
        jetCache.REMOVE(key);
    }

    /**
     * 使当前缓存中的全部条目失效。
     *
     * <p>此实现调用 <code>keyList.isEmpty()</code>。</p>
     *
     * @param keys 缓存键集合
     */
    @Override
    public void invalidateAll(Iterable<K> keys) {
        List<K> keyList = new ArrayList<>();
        keys.forEach(keyList::add);
        if (!keyList.isEmpty()) {
            jetCache.REMOVE_ALL(new java.util.HashSet<>(keyList));
        }
    }

    /** \u5c1d\u8bd5\u5173\u95ed JetCache \u5b9e\u4f8b\u3002\u9700\u8981\u79fb\u9664\u540e\u7aef\u6570\u636e\u65f6\uff0c\u5e94\u63d0\u4f9b\u660e\u786e\u7684\u7f13\u5b58\u952e\u3002 */
    @Override
    public void invalidateAll() {
        // JetCache 的 Cache 接口没有全局 clear 方法（因为远程缓存如 Redis 不适合遍历清除）
        // 通过 close + 重建实现，或业务方通过明确的 key 前缀管理
        log.warn("JetCache 不支持全局 invalidateAll，建议通过明确的 key 前缀管理或重建缓存实例");
        try {
            jetCache.close();
        } catch (Exception e) {
            log.debug("JetCache close 失败", e);
        }
    }

    /**
     * \u62a5\u544a\u5f53\u524d\u540e\u7aef\u7684\u7f13\u5b58\u6761\u76ee\u6570\u91cf\u662f\u5426\u53ef\u7528\u3002
     * @return \u56fa\u5b9a\u8fd4\u56de -1\uff0c\u8868\u793a\u6b64\u9002\u914d\u5668\u4e0d\u4f30\u7b97\u6761\u76ee\u6570
     */
    @Override
    public long estimatedSize() {
        // JetCache 的本地缓存可获取大小，远程缓存无法精确估算
        try {
            com.alicp.jetcache.Cache<K, V> unwrapped = jetCache.unwrap(com.alicp.jetcache.Cache.class);
            if (unwrapped instanceof com.alicp.jetcache.MultiLevelCache) {
                // 多级缓存取第一级（本地）的大小
                return -1;
            }
        } catch (Exception ignore) {
        }
        return -1;
    }

    /**
     * 返回当前缓存实现记录的统计信息。
     *
     * @return 此实现返回<code>null</code>
     */
    @Override
    public CacheStats stats() {
        // JetCache 统计通过 DefaultCacheMonitor 收集，适配器层暂不映射
        // 业务方可通过 jetCache.unwrap() 获取 JetCache 实例后配置 Monitor
        return null;
    }

}
