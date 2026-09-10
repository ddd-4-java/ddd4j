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
package io.ddd4j.mq.redisstream;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @AfterEach
    void clearContext() {
        BaseContext.clear();
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
        Map<String, String> headers = new HashMap<>();
        headers.put(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
        headers.put(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("stable-id", RedisStreamMQClient.messageId(headers));
        assertEquals("legacy-id", RedisStreamMQClient.messageId(Collections.singletonMap(
                MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_shouldPropagateXaddFailure() {
        redis.clients.jedis.UnifiedJedis jedis = mock(redis.clients.jedis.UnifiedJedis.class);
        when(jedis.xadd(anyString(), any(redis.clients.jedis.StreamEntryID.class), anyMap()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisStreamMQClient client = new RedisStreamMQClient(jedis);
        MQProperties mqProperties = new MQProperties();
        mqProperties.setEnabled(true);
        mqProperties.setBroker("redisStream");
        client.init(Collections.<MQListener>emptyList(), mqProperties, new MQEventSerialization() {
            @Override public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
        }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);

        client.close();
        client.close();
        verify(jedis, never()).close();
    }
}
