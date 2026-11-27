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
 * \u9a8c\u8bc1 Redis Stream \u6d88\u606f\u786e\u8ba4\u7684\u5e76\u53d1\u4e0e\u91cd\u8bd5\u884c\u4e3a\uff0c\u4ee5\u53ca Jedis\u3001Redisson \u548c Lettuce \u7684\u547d\u4ee4\u6620\u5c04\u3002
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RedisStreamBrokerAdapterTest {

    /**
     * \u9a8c\u8bc1\u8bf7\u6c42\u91cd\u65b0\u5165\u961f\u7684\u5426\u5b9a\u786e\u8ba4\u4f1a\u7b49\u5f85\u6b63\u5728\u6267\u884c\u7684 XACK\uff0c\u4e14\u6700\u7ec8\u53ea\u53d1\u9001\u4e00\u6b21 XACK\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1\u624b\u52a8\u786e\u8ba4\u901a\u8fc7 Jedis \u5411\u6307\u5b9a\u6d88\u8d39\u8005\u7ec4\u53d1\u9001 XACK\uff0c\u5e76\u6807\u8bb0\u6d88\u606f\u5df2\u786e\u8ba4\u3002 */
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

    /** \u9a8c\u8bc1 XACK \u5931\u8d25\u65f6\u4fdd\u7559\u672a\u786e\u8ba4\u72b6\u6001\uff0c\u540c\u4e00\u786e\u8ba4\u5bf9\u8c61\u53ef\u4ee5\u91cd\u8bd5\u5e76\u6700\u7ec8\u6210\u529f\u3002 */
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

    /** \u9a8c\u8bc1\u8bf7\u6c42\u91cd\u65b0\u5165\u961f\u65f6\u8df3\u8fc7 XACK\uff0c\u8ba9\u6d88\u606f\u7559\u5728\u5f85\u5904\u7406\u5217\u8868\uff0c\u540c\u65f6\u7ed3\u675f\u672c\u6b21\u672c\u5730\u786e\u8ba4\u3002 */
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

    /** \u9a8c\u8bc1 Redisson \u9002\u914d\u5c42\u6dfb\u52a0\u6d88\u606f\u3001\u521b\u5efa\u6d88\u8d39\u8005\u7ec4\u53ca\u53d1\u9001\u786e\u8ba4\uff0c\u5e76\u5c06\u6d88\u606f\u6807\u8bc6\u8f6c\u4e3a\u5b57\u7b26\u4e32\u3002 */
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

    /** \u9a8c\u8bc1 Lettuce \u9002\u914d\u5c42\u5c06\u6dfb\u52a0\u6d88\u606f\u3001\u521b\u5efa\u6d88\u8d39\u8005\u7ec4\u53ca\u786e\u8ba4\u6620\u5c04\u4e3a\u5bf9\u5e94\u7684 Redis \u547d\u4ee4\u3002 */
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
