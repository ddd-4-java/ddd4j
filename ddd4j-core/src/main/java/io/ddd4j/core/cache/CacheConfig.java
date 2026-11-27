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
package io.ddd4j.core.cache;

import java.util.Objects;

import java.util.function.Consumer;

/**
 * 缓存配置（纯 Java 值对象，零框架依赖）。
 *
 * <p>描述缓存的各项参数，由 {@link CacheManager} 在创建缓存时使用。
 * 使用 Builder 模式构建：
 * <pre>{@code
 *   CacheConfig config = CacheConfig.builder()
 *       .maximumSize(10000)
 *       .expireAfterWriteSeconds(300)
 *       .expireAfterAccessSeconds(600)
 *       .refreshAfterWriteSeconds(60)
 *       .cacheType(CacheType.BOTH)
 *       .recordStats(true)
 *       .build();
 *   Cache<String, User> cache = cacheManager.getOrCreateCache("user", config);
 * }</pre>
 *
 * <p>各实现层（ddd4j-cache-core 的 JetCacheAdapter、CaffeineCache 等）
 * 将本配置转换为其底层引擎的具体配置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class CacheConfig {

    /**
     * 默认最大容量
     */
    public static final long DEFAULT_MAXIMUM_SIZE = 1000L;

    /**
     * 缓存名称（业务标识）
     */
    private final String name;
    /**
     * 最大容量
     */
    private final long maximumSize;
    /**
     * 写后过期时间（秒），0 表示不过期
     */
    private final long expireAfterWriteSeconds;
    /**
     * 访问后过期时间（秒），0 表示不过期
     */
    private final long expireAfterAccessSeconds;
    /**
     * 写后刷新时间（秒），0 表示不自动刷新
     */
    private final long refreshAfterWriteSeconds;
    /**
     * 初始容量
     */
    private final int initialCapacity;
    /**
     * 是否记录统计信息
     */
    private final boolean recordStats;
    /**
     * 缓存类型
     */
    private final CacheType cacheType;
    /**
     * 本地缓存限制（多级缓存时本地缓存的最大条目数）
     */
    private final int localLimit;
    /**
     * 是否同步本地缓存（多级缓存时远程变更后是否广播同步本地）
     */
    private final boolean syncLocal;
    /**
     * 移除监听器（键级回调）
     */
    private final transient Consumer<String> removalListener;

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>CacheConfig</code> \u5b9e\u4f8b\u3002
     * @param builder \u79fb\u9664\u76d1\u542c\u5668\uff08\u952e\u7ea7\u56de\u8c03\uff09
     */
    private CacheConfig(Builder builder) {
        this.name = builder.name;
        this.maximumSize = builder.maximumSize;
        this.expireAfterWriteSeconds = builder.expireAfterWriteSeconds;
        this.expireAfterAccessSeconds = builder.expireAfterAccessSeconds;
        this.refreshAfterWriteSeconds = builder.refreshAfterWriteSeconds;
        this.initialCapacity = builder.initialCapacity;
        this.recordStats = builder.recordStats;
        this.cacheType = builder.cacheType;
        this.localLimit = builder.localLimit;
        this.syncLocal = builder.syncLocal;
        this.removalListener = builder.removalListener;
    }

    /**
     * 创建 Builder。
     *
     * @param name 缓存名称（业务标识，不可为空）
     * @return Builder 实例
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * 创建 Builder（从已有配置复制）。
     *
     * @param source 源配置
     * @return Builder 实例
     */
    public static Builder builder(CacheConfig source) {
        return new Builder(source);
    }

    // ==================== Getters ====================

    /**
     * \u83b7\u53d6\u7f13\u5b58\u540d\u79f0\u3002
     * @return \u7f13\u5b58\u540d\u79f0
     */
    public String getName() {
        return name;
    }

    /**
     * \u83b7\u53d6\u6700\u5927\u5bb9\u91cf\u3002
     * @return \u6700\u5927\u5bb9\u91cf
     */
    public long getMaximumSize() {
        return maximumSize;
    }

    /**
     * \u83b7\u53d6\u5199\u540e\u8fc7\u671f\u79d2\u6570\u3002
     * @return \u5199\u540e\u8fc7\u671f\u79d2\u6570
     */
    public long getExpireAfterWriteSeconds() {
        return expireAfterWriteSeconds;
    }

    /**
     * \u83b7\u53d6\u8bbf\u95ee\u540e\u8fc7\u671f\u79d2\u6570\u3002
     * @return \u8bbf\u95ee\u540e\u8fc7\u671f\u79d2\u6570
     */
    public long getExpireAfterAccessSeconds() {
        return expireAfterAccessSeconds;
    }

    /**
     * \u83b7\u53d6\u5199\u540e\u5237\u65b0\u79d2\u6570\u3002
     * @return \u5199\u540e\u5237\u65b0\u79d2\u6570
     */
    public long getRefreshAfterWriteSeconds() {
        return refreshAfterWriteSeconds;
    }

    /**
     * \u83b7\u53d6\u521d\u59cb\u5bb9\u91cf\u3002
     * @return \u521d\u59cb\u5bb9\u91cf
     */
    public int getInitialCapacity() {
        return initialCapacity;
    }

    /**
     * \u83b7\u53d6\u662f\u5426\u8bb0\u5f55\u7edf\u8ba1\u4fe1\u606f\u3002
     * @return \u662f\u5426\u8bb0\u5f55\u7edf\u8ba1\u4fe1\u606f
     */
    public boolean isRecordStats() {
        return recordStats;
    }

    /**
     * \u83b7\u53d6\u7f13\u5b58\u7c7b\u578b\u3002
     * @return \u7f13\u5b58\u7c7b\u578b
     */
    public CacheType getCacheType() {
        return cacheType;
    }

    /**
     * \u83b7\u53d6\u672c\u5730\u7f13\u5b58\u6700\u5927\u6761\u76ee\u6570\u3002
     * @return \u672c\u5730\u7f13\u5b58\u6700\u5927\u6761\u76ee\u6570
     */
    public int getLocalLimit() {
        return localLimit;
    }

    /**
     * \u83b7\u53d6\u662f\u5426\u540c\u6b65\u672c\u5730\u7f13\u5b58\u3002
     * @return \u662f\u5426\u540c\u6b65\u672c\u5730\u7f13\u5b58
     */
    public boolean isSyncLocal() {
        return syncLocal;
    }

    /**
     * \u83b7\u53d6\u79fb\u9664\u76d1\u542c\u5668\u3002
     * @return \u79fb\u9664\u76d1\u542c\u5668
     */
    public Consumer<String> getRemovalListener() {
        return removalListener;
    }

    // ==================== Builder ====================

    /**
     * 缓存配置构建器。
     */
    public static final class Builder {

        /** \u6784\u5efa\u540e\u7528\u4e8e\u6ce8\u518c\u7f13\u5b58\u7684\u540d\u79f0\u3002 */
        private final String name;
        /** \u7f13\u5b58\u5141\u8bb8\u4fdd\u7559\u7684\u6700\u5927\u6761\u76ee\u6570\u3002 */
        private long maximumSize = DEFAULT_MAXIMUM_SIZE;
        /** \u81ea\u6700\u540e\u4e00\u6b21\u5199\u5165\u8d77\u7684\u8fc7\u671f\u79d2\u6570\u3002 */
        private long expireAfterWriteSeconds = 0L;
        /** \u81ea\u6700\u540e\u4e00\u6b21\u8bbf\u95ee\u8d77\u7684\u8fc7\u671f\u79d2\u6570\u3002 */
        private long expireAfterAccessSeconds = 0L;
        /** \u81ea\u5199\u5165\u8d77\u5141\u8bb8\u81ea\u52a8\u5237\u65b0\u7f13\u5b58\u503c\u7684\u79d2\u6570\u3002 */
        private long refreshAfterWriteSeconds = 0L;
        /** \u7f13\u5b58\u6784\u5efa\u540e\u9884\u7559\u7684\u521d\u59cb\u6761\u76ee\u5bb9\u91cf\u3002 */
        private int initialCapacity = 0;
        /** \u662f\u5426\u542f\u7528\u547d\u4e2d\u3001\u52a0\u8f7d\u7b49\u7f13\u5b58\u7edf\u8ba1\u3002 */
        private boolean recordStats = false;
        /** \u7531\u7f13\u5b58\u7ba1\u7406\u5668\u9009\u62e9\u7684\u7f13\u5b58\u5b9e\u73b0\u7c7b\u578b\u3002 */
        private CacheType cacheType = CacheType.LOCAL;
        /** \u591a\u7ea7\u7f13\u5b58\u7684\u672c\u5730\u5c42\u5141\u8bb8\u4fdd\u7559\u7684\u6700\u5927\u6761\u76ee\u6570\u3002 */
        private int localLimit = 100;
        /** \u662f\u5426\u5c06\u8fdc\u7aef\u66f4\u65b0\u540c\u6b65\u5230\u672c\u5730\u7f13\u5b58\u3002 */
        private boolean syncLocal = false;
        /** \u6761\u76ee\u4ece\u7f13\u5b58\u4e2d\u79fb\u9664\u540e\u63a5\u6536\u901a\u77e5\u7684\u76d1\u542c\u5668\u3002 */
        private Consumer<String> removalListener = null;

        /**
         * 构造器（指定缓存名称）。
         *
         * @param name 缓存名称（业务标识）
         */
        public Builder(String name) {
            if (Objects.isNull(name) || io.ddd4j.kit.lang.StrKit.isEmpty(name)) {
                throw new IllegalArgumentException("缓存名称不能为空");
            }
            this.name = name;
        }

        /**
         * 构造器（从已有配置复制）。
         *
         * @param source 源配置
         */
        public Builder(CacheConfig source) {
            this.name = source.name;
            this.maximumSize = source.maximumSize;
            this.expireAfterWriteSeconds = source.expireAfterWriteSeconds;
            this.expireAfterAccessSeconds = source.expireAfterAccessSeconds;
            this.refreshAfterWriteSeconds = source.refreshAfterWriteSeconds;
            this.initialCapacity = source.initialCapacity;
            this.recordStats = source.recordStats;
            this.cacheType = source.cacheType;
            this.localLimit = source.localLimit;
            this.syncLocal = source.syncLocal;
            this.removalListener = source.removalListener;
        }

        /**
         * 设置最大容量。
         * @param maximumSize 长整型数值
         * @return 返回的 Builder 结果
         */
        public Builder maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * 设置写后过期时间（秒）。
         * @param seconds 秒数
         * @return 返回的 Builder 结果
         */
        public Builder expireAfterWriteSeconds(long seconds) {
            this.expireAfterWriteSeconds = seconds;
            return this;
        }

        /**
         * 设置访问后过期时间（秒）。
         * @param seconds 秒数
         * @return 返回的 Builder 结果
         */
        public Builder expireAfterAccessSeconds(long seconds) {
            this.expireAfterAccessSeconds = seconds;
            return this;
        }

        /**
         * 设置写后刷新时间（秒）。
         * @param seconds 秒数
         * @return 返回的 Builder 结果
         */
        public Builder refreshAfterWriteSeconds(long seconds) {
            this.refreshAfterWriteSeconds = seconds;
            return this;
        }

        /**
         * 设置初始容量。
         * @param initialCapacity 整型数值
         * @return 返回的 Builder 结果
         */
        public Builder initialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        /**
         * 设置是否记录统计信息。
         * @param recordStats 布尔值
         * @return 返回的 Builder 结果
         */
        public Builder recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        /**
         * 设置缓存类型。
         * @param cacheType 缓存类型
         * @return 返回的 Builder 结果
         */
        public Builder cacheType(CacheType cacheType) {
            this.cacheType = cacheType;
            return this;
        }

        /**
         * 设置本地缓存限制（多级缓存时本地缓存最大条目数）。
         * @param localLimit 本地上限
         * @return 返回的 Builder 结果
         */
        public Builder localLimit(int localLimit) {
            this.localLimit = localLimit;
            return this;
        }

        /**
         * 设置是否同步本地缓存（多级缓存时远程变更是否广播同步本地）。
         * @param syncLocal 同步本地
         * @return 返回的 Builder 结果
         */
        public Builder syncLocal(boolean syncLocal) {
            this.syncLocal = syncLocal;
            return this;
        }

        /**
         * 设置移除监听器。
         * @param removalListener 消费逻辑
         * @return 返回的 Builder 结果
         */
        public Builder removalListener(Consumer<String> removalListener) {
            this.removalListener = removalListener;
            return this;
        }

        /**
         * 构建不可变的 CacheConfig 实例。
         *
         * @return CacheConfig 实例
         */
        public CacheConfig build() {
            return new CacheConfig(this);
        }

    }

}
