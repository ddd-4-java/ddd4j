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

/**
 * 验证缓存适配器的默认 JSON 映射器省略空属性。
 */
class CacheDefaultMapperContractTest {

    /**
     * 创建指定接口的动态代理供缓存适配器构造使用。
     *
     * @param <T>  客户端接口类型
     * @param type 代理实现的客户端接口
     * @return 无需实际网络连接的客户端代理
     */
    @SuppressWarnings("unchecked")
    private static <T> T client(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 验证<code>json</code> 包含 <code>"present"</code>。
     *
     * @throws Exception 反射访问被测成员、处理测试数据的序列化时失败
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
     * 同时提供有值属性和缺失属性的序列化测试样本。
     */
    public static final class NullableValue {

        /**
         * 为当前用例提供固定的 <code>getPresent</code> 返回值。
         *
         * @return <code>"value"</code>
         */
        public String getPresent() {
            return "value";
        }

        /**
         * 为当前用例提供固定的 <code>getMissing</code> 返回值。
         *
         * @return <code>null</code>
         */
        public String getMissing() {
            return null;
        }
    }
}
