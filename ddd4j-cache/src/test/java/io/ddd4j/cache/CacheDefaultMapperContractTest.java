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

/** \u9a8c\u8bc1\u7f13\u5b58\u9002\u914d\u5668\u7684\u9ed8\u8ba4 JSON \u6620\u5c04\u5668\u7701\u7565\u7a7a\u5c5e\u6027\u3002 */
class CacheDefaultMapperContractTest {

    /**
     * \u9a8c\u8bc1<code>json</code> \u5305\u542b <code>"present"</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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

    /**
     * \u521b\u5efa\u6307\u5b9a\u63a5\u53e3\u7684\u52a8\u6001\u4ee3\u7406\u4f9b\u7f13\u5b58\u9002\u914d\u5668\u6784\u9020\u4f7f\u7528\u3002
     * @param <T> \u5ba2\u6237\u7aef\u63a5\u53e3\u7c7b\u578b
     * @param type \u4ee3\u7406\u5b9e\u73b0\u7684\u5ba2\u6237\u7aef\u63a5\u53e3
     * @return \u65e0\u9700\u5b9e\u9645\u7f51\u7edc\u8fde\u63a5\u7684\u5ba2\u6237\u7aef\u4ee3\u7406
     */
    @SuppressWarnings("unchecked")
    private static <T> T client(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** \u540c\u65f6\u63d0\u4f9b\u6709\u503c\u5c5e\u6027\u548c\u7f3a\u5931\u5c5e\u6027\u7684\u5e8f\u5217\u5316\u6d4b\u8bd5\u6837\u672c\u3002 */
    public static final class NullableValue {

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>getPresent</code> \u8fd4\u56de\u503c\u3002
         * @return <code>"value"</code>
         */
        public String getPresent() {
            return "value";
        }

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>getMissing</code> \u8fd4\u56de\u503c\u3002
         * @return <code>null</code>
         */
        public String getMissing() {
            return null;
        }
    }
}
