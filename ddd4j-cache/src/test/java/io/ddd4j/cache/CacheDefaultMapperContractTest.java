package io.ddd4j.cache;

import io.ddd4j.cache.lettuce.LettuceCache;
import io.ddd4j.cache.memcached.MemcachedCache;
import io.ddd4j.cache.redisson.RedissonCache;
import io.ddd4j.core.cache.CacheConfig;
import io.lettuce.core.api.sync.RedisCommands;
import net.rubyeye.xmemcached.MemcachedClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class CacheDefaultMapperContractTest {

    @Test
    void adapterDefaultMappersOmitNullProperties() throws Exception {
        CacheConfig config = CacheConfig.builder("mapper-contract").build();
        Object[] caches = {
                new LettuceCache<>(client(RedisCommands.class), config, NullableValue.class, ignored -> null),
                new MemcachedCache<>(client(MemcachedClient.class), config, NullableValue.class, ignored -> null),
                new RedissonCache<>(client(RedissonClient.class), config, NullableValue.class, ignored -> null)
        };

        for (Object cache : caches) {
            Field mapperField = cache.getClass().getDeclaredField("objectMapper");
            mapperField.setAccessible(true);
            Object mapper = mapperField.get(cache);
            String json = (String) mapper.getClass()
                    .getMethod("writeValueAsString", Object.class)
                    .invoke(mapper, new NullableValue());
            assertThat(json).contains("present").doesNotContain("missing");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T client(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    public static final class NullableValue {

        public String getPresent() {
            return "value";
        }

        public String getMissing() {
            return null;
        }
    }
}
