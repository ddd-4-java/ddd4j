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
package io.ddd4j.extension.license;

import io.ddd4j.cache.CacheKit;
import lombok.extern.slf4j.Slf4j;

/**
 * License 缓存常量与缓存域的懒注册工具。
 *
 * <p>所有 License 验证结果统一缓存到 {@code CacheKit}，缓存域名为 {@link #BIZ_LICENSE}，
 * 缓存 key 为证书 subject（一个部署实例一个 subject）。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 启动期或首次使用前（幂等）
 *   LicenseCache.init(300);
 *   // 读写
 *   LicenseCache.put("ioserver", info);
 *   LicenseInfo info = LicenseCache.get("ioserver");
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class LicenseCache {

    /**
     * 缓存业务标识
     */
    public static final String BIZ_LICENSE = "license";
    /**
     * 默认缓存 TTL（秒）
     */
    public static final long DEFAULT_TTL_SECONDS = 300L;

    private LicenseCache() {
    }

    /**
     * 幂等地注册 License 缓存域。
     *
     * <p>若已注册则不重复构建；若未注册，则按默认类型（Caffeine）构建写后过期的本地缓存。
     *
     * @param ttlSeconds 缓存过期时间（秒）
     */
    public static synchronized void init(long ttlSeconds) {
        if (CacheKit.getCacheNames().contains(BIZ_LICENSE)) {
            return;
        }
        long effectiveTtlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        CacheKit.build(BIZ_LICENSE, effectiveTtlSeconds);
        log.info("License 缓存域已注册: biz={}, ttl={}s", BIZ_LICENSE, effectiveTtlSeconds);
    }

    /**
     * 以默认 TTL 注册缓存域。
     */
    public static void init() {
        init(DEFAULT_TTL_SECONDS);
    }

    /**
     * 写入缓存。
     *
     * @param subject 缓存 key
     * @param info    缓存值
     */
    public static void put(String subject, LicenseInfo info) {
        ensureRegistered();
        CacheKit.put(BIZ_LICENSE, subject, info);
    }

    /**
     * 读取缓存。
     *
     * @param subject 缓存 key
     * @return 缓存值，不存在返回 null
     */
    public static LicenseInfo get(String subject) {
        ensureRegistered();
        return CacheKit.get(BIZ_LICENSE, subject);
    }

    /**
     * 移除指定 subject 的缓存项。
     *
     * @param subject 缓存 key
     */
    public static void invalidate(String subject) {
        ensureRegistered();
        CacheKit.invalidate(BIZ_LICENSE, subject);
    }

    /**
     * 判断缓存是否包含指定 subject。
     *
     * @param subject 缓存 key
     * @return true 表示存在
     */
    public static boolean exists(String subject) {
        ensureRegistered();
        return CacheKit.exists(BIZ_LICENSE, subject);
    }

    /**
     * 清空整个 License 缓存域。
     */
    public static void invalidateAll() {
        ensureRegistered();
        CacheKit.invalidateAll(BIZ_LICENSE);
    }

    /**
     * 确保缓存域已注册；未注册时以默认 TTL 懒注册。
     */
    private static void ensureRegistered() {
        if (!CacheKit.getCacheNames().contains(BIZ_LICENSE)) {
            init(DEFAULT_TTL_SECONDS);
        }
    }
}
