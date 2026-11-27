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

import java.util.Collections;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Redis Stream 消息确认的并发与重试行为，以及 Jedis、Redisson 和 Lettuce 的命令映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RedisStreamBrokerAdapterTest {

    /**
     * 验证请求重新入队的否定确认会等待正在执行的 XACK，且最终只发送一次 XACK。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void nackMustWaitForInFlightXackBeforeClaimingRequeue() throws Exception {
        RedisStreamOperations operations = mock(RedisStreamOperations.class);
        CountDownLatch xackEntered = new CountDownLatch(1);
        CountDownLatch releaseXack = new CountDownLatch(1);
        doAnswer(invocation -> {
            xackEntered.countDown();
            assertTrue(releaseXack.await(5, TimeUnit.SECONDS));
            return null;
        }).when(operations).ack("orders", "workers", "1-0");
        RedisStreamAcknowledgment acknowledgment = new RedisStreamAcknowledgment(
                operations, "orders", "workers", "1-0", null, "msg-1", null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> ackCall = executor.submit(() -> {
                acknowledgment.ack();
            });
            assertTrue(xackEntered.await(5, TimeUnit.SECONDS));
            CountDownLatch nackStarted = new CountDownLatch(1);
            Future<?> nackCall = executor.submit(() -> {
                nackStarted.countDown();
                acknowledgment.nack(true);
            });
            assertTrue(nackStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> nackCall.get(200, TimeUnit.MILLISECONDS));
            releaseXack.countDown();
            ackCall.get(5, TimeUnit.SECONDS);
            nackCall.get(5, TimeUnit.SECONDS);
            verify(operations).ack("orders", "workers", "1-0");
        } finally {
            releaseXack.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 验证手动确认通过 Jedis 向指定消费者组发送 XACK，并标记消息已确认。
     */
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

    /**
     * 验证 XACK 失败时保留未确认状态，同一确认对象可以重试并最终成功。
     */
    @Test
    void failedXackMustRemainRetryableOnSameAcknowledgment() {
        UnifiedJedis jedis = mock(UnifiedJedis.class);
        StreamEntryID id = new StreamEntryID("3-0");
        when(jedis.xack("orders", "workers", id))
                .thenThrow(new IllegalStateException("ack lost"))
                .thenReturn(1L);
        RedisStreamAcknowledgment acknowledgment = new RedisStreamAcknowledgment(
                jedis, "orders", "workers", id, "msg-3", "corr-3");

        assertThrows(IllegalStateException.class, acknowledgment::ack);
        assertFalse(acknowledgment.isAcknowledged());
        acknowledgment.ack();
        assertTrue(acknowledgment.isAcknowledged());
        verify(jedis, times(2)).xack("orders", "workers", id);
    }

    /**
     * 验证请求重新入队时跳过 XACK，让消息留在待处理列表，同时结束本次本地确认。
     */
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

    /**
     * 验证 Redisson 适配层添加消息、创建消费者组及发送确认，并将消息标识转为字符串。
     */
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

    /**
     * 验证 Lettuce 适配层将添加消息、创建消费者组及确认映射为对应的 Redis 命令。
     */
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
