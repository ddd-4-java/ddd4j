package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RedisStreamMQClient} 客户端基础属性测试。
 *
 * <p>不依赖 Redis 连接，验证：
 * <ul>
 *   <li>impl() 返回正确的 broker 标识</li>
 *   <li>defaultConcat() 返回 Redis 命名习惯的冒号分隔符</li>
 *   <li>tagHeaderKey() 返回 ddd4jTag</li>
 *   <li>构造函数正确初始化</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RedisStreamMQClientTest {

    private RedisStreamMQClient clientWithInjectedJedis;
    private RedisStreamMQProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RedisStreamMQProperties();
        clientWithInjectedJedis = new RedisStreamMQClient((redis.clients.jedis.UnifiedJedis) null);
    }

    @Test
    void impl_shouldReturnRedisStream() {
        assertEquals("redisStream", clientWithInjectedJedis.impl());
    }

    @Test
    void defaultConcat_shouldReturnColon() {
        assertEquals(":", clientWithInjectedJedis.defaultConcat());
    }

    @Test
    void tagHeaderKey_shouldReturnDdd4jTag() {
        assertEquals("ddd4jTag", clientWithInjectedJedis.tagHeaderKey());
    }

    @Test
    void propertiesConstructor_shouldNotThrow() {
        RedisStreamMQClient client = new RedisStreamMQClient(properties);
        assertNotNull(client);
        assertEquals("redisStream", client.impl());
    }

    @Test
    void injectedJedisConstructor_shouldNotThrow() {
        RedisStreamMQClient client = new RedisStreamMQClient((redis.clients.jedis.UnifiedJedis) null);
        assertNotNull(client);
        assertEquals("redisStream", client.impl());
    }

    @Test
    void defaultProperties_shouldHaveSensibleValues() {
        RedisStreamMQProperties props = new RedisStreamMQProperties();
        assertNotNull(props);
        assertNotNull(props.getNamespace());
    }

    @Test
    void namespaceDefault_shouldNotBeNull() {
        assertNotNull(properties.getNamespace());
    }

    @Test
    void messageId_shouldPreferStableHeaderAndReadLegacyHeader() {
        assertEquals("stable-id", RedisStreamMQClient.messageId(Map.of(
                MessageHeaders.HEADER_MESSAGE_ID, "stable-id",
                MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id")));
        assertEquals("legacy-id", RedisStreamMQClient.messageId(Map.of(
                MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id")));
    }
}
