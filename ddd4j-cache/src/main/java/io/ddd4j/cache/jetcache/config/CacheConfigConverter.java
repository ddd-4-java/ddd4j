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
package io.ddd4j.cache.jetcache.config;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import io.ddd4j.core.cache.CacheConfig;

import java.time.Duration;

/**
 * 缓存配置转换器：ddd4j {@link CacheConfig} → JetCache {@link QuickConfig}。
 *
 * <p>将 ddd4j 纯 Java 的缓存配置转换为 JetCache 的 QuickConfig，
 * 以便通过 JetCache {@code CacheManager.getOrCreateCache(QuickConfig)} 创建缓存实例。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class CacheConfigConverter {

    private CacheConfigConverter() {
    }

    /**
     * 将 ddd4j CacheConfig 转换为 JetCache QuickConfig。
     *
     * @param config    ddd4j 缓存配置
     * @param cacheArea JetCache 区域名称（用于区分不同的远程缓存实例，如 "default"）
     * @return JetCache QuickConfig
     */
    public static QuickConfig toQuickConfig(CacheConfig config, String cacheArea) {
        QuickConfig.Builder builder = QuickConfig.newBuilder(cacheArea, config.getName());

        // 缓存类型
        builder.cacheType(toJetCacheType(config.getCacheType()));

        // 过期时间（JetCache 使用 Duration）
        if (config.getExpireAfterWriteSeconds() > 0) {
            builder.expire(Duration.ofSeconds(config.getExpireAfterWriteSeconds()));
        }

        // 本地缓存限制（多级缓存时本地缓存最大条目数）
        if (config.getLocalLimit() > 0) {
            builder.localLimit(config.getLocalLimit());
        }

        // 是否同步本地缓存（多级缓存时远程变更广播同步本地）
        builder.syncLocal(config.isSyncLocal());

        return builder.build();
    }

    /**
     * ddd4j CacheType → JetCache CacheType。
     *
     * @param cacheType ddd4j 缓存类型
     * @return JetCache 缓存类型
     */
    public static CacheType toJetCacheType(io.ddd4j.core.cache.CacheType cacheType) {
        switch (cacheType) {
            case LOCAL:
                return CacheType.LOCAL;
            case REMOTE:
                return CacheType.REMOTE;
            case BOTH:
                return CacheType.BOTH;
            default:
                return CacheType.LOCAL;
        }
    }

}
