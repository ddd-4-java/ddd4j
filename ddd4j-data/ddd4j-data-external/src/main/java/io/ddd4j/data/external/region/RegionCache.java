package io.ddd4j.data.external.region;

import java.time.Duration;

/**
 * Cache abstraction used by region templates.
 */
public interface RegionCache {

    RegionCache NONE = new RegionCache() {

        @Override
        public String getString(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
        }
    };

    String getString(String key);

    void set(String key, String value, Duration ttl);

    static RegionCache none() {
        return NONE;
    }
}
