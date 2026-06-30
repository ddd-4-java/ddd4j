package io.ddd4j.data.external.region;

import org.springframework.util.StringUtils;

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

    public static String ipRegion(String ip) {
        return key("ip:region", ip);
    }

    public static String ipLocation(String ip) {
        return key("ip:location", ip);
    }

    public static String baiduLocation(String ip) {
        return key("baidu:ip:location", IpAddressKit.ip2long(ip));
    }

    public static String pconlineLocation(String ip) {
        return key("pconline:ip:location", ip);
    }

    private static String key(Object... parts) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        joiner.add(REDIS_PREFIX);
        for (Object part : parts) {
            if (Objects.nonNull(part) && StringUtils.hasText(part.toString())) {
                joiner.add(part.toString());
            }
        }
        return joiner.toString();
    }
}
