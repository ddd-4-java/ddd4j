package io.ddd4j.data.external.region;

import java.time.Duration;

/**
 * Cache abstraction used by region templates.
 */
public interface RegionCache {

    /** \u4e0d\u6267\u884c\u7f13\u5b58\u64cd\u4f5c\u7684\u5b9e\u73b0\u3002 */
    RegionCache NONE = new RegionCache() {

        @Override
        public String getString(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
        }
    };

    /**
     * \u8fd4\u56de\u4e0d\u6267\u884c\u7f13\u5b58\u64cd\u4f5c\u7684\u5b9e\u73b0\u3002
     * @return \u7a7a\u7f13\u5b58\u5b9e\u73b0
     */
    static RegionCache none() {
        return NONE;
    }

    /**
     * \u8bfb\u53d6\u7f13\u5b58\u5b57\u7b26\u4e32\u3002
     * @param key \u7f13\u5b58\u952e
     * @return \u7f13\u5b58\u503c\uff1b\u672a\u547d\u4e2d\u65f6\u4e3a null
     */
    String getString(String key);

    /**
     * \u5199\u5165\u5e26\u6709\u6548\u671f\u7684\u7f13\u5b58\u5b57\u7b26\u4e32\u3002
     * @param key \u7f13\u5b58\u952e
     * @param value \u7f13\u5b58\u503c
     * @param ttl \u6709\u6548\u671f
     */
    void set(String key, String value, Duration ttl);
}
