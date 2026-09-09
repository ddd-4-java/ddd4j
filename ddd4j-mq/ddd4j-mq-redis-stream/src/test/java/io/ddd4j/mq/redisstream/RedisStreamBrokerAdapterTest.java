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

import java.util.Collections;
import io.ddd4j.mq.redisstream.lettuce.LettuceRedisStreamOperations;
import io.ddd4j.mq.redisstream.redisson.RedissonRedisStreamOperations;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis Stream Acknowledgment 及 Jedis / Redisson / Lettuce 三种客户端 SPI 适配契约测试。
 *
 * <p>发布与消费已整合进 {@link RedisStreamClient}，相关单测见其模块；此处保留 SPI 层契约。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RedisStreamBrokerAdapterTest {

    @Test
    void manualAckShouldMapToXack() {
        UnifiedJedis jedis = mock(UnifiedJedis.class);
        StreamEntryID id = new StreamEntryID("1-0");
        RedisStreamAcknowledgment ack = new RedisStreamAcknowledgment(
                jedis,
                "sales.order.paid",
                "sample",
                id,
                "msg-1",
                "corr-1");

        ack.ack(false);

        verify(jedis).xack("sales.order.paid", "sample", id);
        assertTrue(ack.isAcknowledged());
    }

    @Test
    void nackWithRequeueShouldLeaveEntryPendingAndMarkItHandled() {
        UnifiedJedis jedis = mock(UnifiedJedis.class);
        StreamEntryID id = new StreamEntryID("2-0");
        RedisStreamAcknowledgment acknowledgment = new RedisStreamAcknowledgment(
                jedis, "orders", "workers", id, "msg-2", null);

        acknowledgment.nack(true);

        verify(jedis, never()).xack(anyString(), anyString(), any(StreamEntryID.class));
        assertTrue(acknowledgment.isAcknowledged());
    }

    @Test
    void redissonOperationsShouldMapStreamCommands() {
        RedissonClient client = mock(RedissonClient.class);
        RStream<String, String> stream = mock(RStream.class);
        when(client.<String, String>getStream("sales.order.paid")).thenReturn(stream);
        when(stream.add(any(StreamAddArgs.class))).thenReturn(new StreamMessageId(1L, 0L));
        RedissonRedisStreamOperations operations = new RedissonRedisStreamOperations(client);

        String id = operations.add("sales.order.paid", Collections.singletonMap("payload", "body"));
        operations.createGroup("sales.order.paid", "sample");
        operations.ack("sales.order.paid", "sample", "1-0");

        assertEquals("1-0", id);
        verify(stream).add(any(StreamAddArgs.class));
        verify(stream).createGroup(any(StreamCreateGroupArgs.class));
        verify(stream).ack("sample", new StreamMessageId(1L, 0L));
    }

    @Test
    void lettuceOperationsShouldMapStreamCommands() {
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(commands.xadd(eq("sales.order.paid"), any(XAddArgs.class), anyMap())).thenReturn("1-0");
        LettuceRedisStreamOperations operations = new LettuceRedisStreamOperations(commands);

        String id = operations.add("sales.order.paid", Collections.singletonMap("payload", "body"));
        operations.createGroup("sales.order.paid", "sample");
        operations.ack("sales.order.paid", "sample", "1-0");

        assertEquals("1-0", id);
        verify(commands).xadd(eq("sales.order.paid"), any(XAddArgs.class), anyMap());
        verify(commands).xgroupCreate(any(XReadArgs.StreamOffset.class), eq("sample"), any(XGroupCreateArgs.class));
        verify(commands).xack("sales.order.paid", "sample", "1-0");
    }
}
