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
package io.ddd4j.data.external.region;

import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Redis-compatible cache keys formerly provided by redistpl-plus starter.
 */
public final class RegionCacheKeys {

    private static final String REDIS_PREFIX = "rds";
    private static final String DELIMITER = ":";

    private RegionCacheKeys() {
    }

    /**
     * 生成 IP 地区缓存的键
     *
     * @param ip IP 地址
     * @return 缓存键
     */
    public static String ipRegion(String ip) {
        return key("ip:region", ip);
    }

    /**
     * 生成 IP 位置缓存的键
     *
     * @param ip IP 地址
     * @return 缓存键
     */
    public static String ipLocation(String ip) {
        return key("ip:location", ip);
    }

    /**
     * 生成百度 IP 位置缓存的键
     *
     * @param ip IP 地址
     * @return 缓存键
     */
    public static String baiduLocation(String ip) {
        return key("baidu:ip:location", IpAddressKit.ip2long(ip));
    }

    /**
     * 生成太平洋网络 IP 位置缓存的键
     *
     * @param ip IP 地址
     * @return 缓存键
     */
    public static String pconlineLocation(String ip) {
        return key("pconline:ip:location", ip);
    }

    private static String key(Object... parts) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        joiner.add(REDIS_PREFIX);
        for (Object part : parts) {
            if (Objects.nonNull(part) && StrKit.hasText(part.toString())) {
                joiner.add(part.toString());
            }
        }
        return joiner.toString();
    }
}
