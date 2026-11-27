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

    /** \u6d4b\u8bd5 Redis \u64cd\u4f5c\u65f6\u6ce8\u5165\u4e86\u5916\u90e8 Jedis \u7684\u5ba2\u6237\u7aef\u3002 */
    private RedisStreamMQClient clientWithInjectedJedis;
    /** \u88c5\u914d\u9694\u79bb\u6d4b\u8bd5\u8fd0\u884c\u73af\u5883\u65f6\u4f7f\u7528\u7684\u914d\u7f6e\u5c5e\u6027\u3002 */
    private RedisStreamMQProperties properties;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>properties = new RedisStreamMQProperties()</code>\u3001<code>clientWithInjectedJedis = new RedisStreamMQClient((redis.clients.jedis.UnifiedJedis)null)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        properties = new RedisStreamMQProperties();
        clientWithInjectedJedis = new RedisStreamMQClient((redis.clients.jedis.UnifiedJedis) null);
    }

    /** \u9a8c\u8bc1<code>clientWithInjectedJedis.impl()</code> \u7b49\u4e8e <code>"redisStream"</code>\u3002 */
    @Test
    void impl_shouldReturnRedisStream() {
        assertEquals("redisStream", clientWithInjectedJedis.impl());
    }

    /** \u9a8c\u8bc1<code>clientWithInjectedJedis.defaultConcat()</code> \u7b49\u4e8e <code>":"</code>\u3002 */
    @Test
    void defaultConcat_shouldReturnColon() {
        assertEquals(":", clientWithInjectedJedis.defaultConcat());
    }

    /** \u9a8c\u8bc1<code>clientWithInjectedJedis.tagHeaderKey()</code> \u7b49\u4e8e <code>"ddd4jTag"</code>\u3002 */
    @Test
    void tagHeaderKey_shouldReturnDdd4jTag() {
        assertEquals("ddd4jTag", clientWithInjectedJedis.tagHeaderKey());
    }

    /** \u9a8c\u8bc1<code>client</code>\u975e\u7a7a\uff0c<code>client.impl()</code> \u7b49\u4e8e <code>"redisStream"</code>\u3002 */
    @Test
    void propertiesConstructor_shouldNotThrow() {
        RedisStreamMQClient client = new RedisStreamMQClient(properties);
        assertNotNull(client);
        assertEquals("redisStream", client.impl());
    }

    /** \u9a8c\u8bc1<code>client</code>\u975e\u7a7a\uff0c<code>client.impl()</code> \u7b49\u4e8e <code>"redisStream"</code>\u3002 */
    @Test
    void injectedJedisConstructor_shouldNotThrow() {
        RedisStreamMQClient client = new RedisStreamMQClient((redis.clients.jedis.UnifiedJedis) null);
        assertNotNull(client);
        assertEquals("redisStream", client.impl());
    }

    /** \u9a8c\u8bc1<code>props</code>\u975e\u7a7a\uff0c<code>props.getNamespace()</code>\u975e\u7a7a\u3002 */
    @Test
    void defaultProperties_shouldHaveSensibleValues() {
        RedisStreamMQProperties props = new RedisStreamMQProperties();
        assertNotNull(props);
        assertNotNull(props.getNamespace());
    }

    /** \u9a8c\u8bc1<code>properties.getNamespace()</code>\u975e\u7a7a\u3002 */
    @Test
    void namespaceDefault_shouldNotBeNull() {
        assertNotNull(properties.getNamespace());
    }

    /** \u9a8c\u8bc1<code>RedisStreamMQClient.messageId(headers)</code> \u7b49\u4e8e <code>"stable-id"</code>\u3002 */
    @Test
    void messageId_shouldPreferStableHeaderAndReadLegacyHeader() {
        assertEquals("stable-id", RedisStreamMQClient.messageId(Map.of(
                MessageHeaders.HEADER_MESSAGE_ID, "stable-id",
                MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id")));
        assertEquals("legacy-id", RedisStreamMQClient.messageId(Map.of(
                MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id")));
    }
}
