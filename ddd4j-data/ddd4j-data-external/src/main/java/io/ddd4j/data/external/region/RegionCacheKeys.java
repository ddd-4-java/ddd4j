package io.ddd4j.data.external.region;

import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Redis-compatible cache keys formerly provided by redistpl-plus starter.
 */
public final class RegionCacheKeys {

    /** \u5730\u533a\u67e5\u8be2\u7f13\u5b58\u952e\u7684\u56fa\u5b9a\u524d\u7f00\u3002 */
    private static final String REDIS_PREFIX = "rds";
    /** \u62fc\u63a5\u7f13\u5b58\u952e\u5404\u6bb5\u65f6\u4f7f\u7528\u7684\u5206\u9694\u7b26\u3002 */
    private static final String DELIMITER = ":";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>RegionCacheKeys</code> \u5b9e\u4f8b\u3002 */
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

    /**
     * \u62fc\u63a5\u7f13\u5b58\u524d\u7f00\u548c\u975e\u7a7a\u767d\u7684\u952e\u6bb5\u3002
     * @param parts \u6309\u7167\u7f13\u5b58\u8bc6\u522b\u987a\u5e8f\u63d0\u4f9b\u7684\u952e\u6bb5
     * @return \u5730\u533a\u67e5\u8be2\u4f7f\u7528\u7684\u5b8c\u6574\u7f13\u5b58\u952e
     */
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
