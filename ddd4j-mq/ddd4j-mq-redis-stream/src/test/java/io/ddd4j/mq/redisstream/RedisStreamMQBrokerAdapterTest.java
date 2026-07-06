package io.ddd4j.mq.redisstream;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.redisstream.lettuce.LettuceRedisStreamOperations;
import io.ddd4j.mq.redisstream.redisson.RedissonRedisStreamOperations;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis Stream adapter contract tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RedisStreamBrokerAdapterTest {

    private static ListenerDefinition definition(String tags) throws Exception {
        Method method = SampleConsumer.class.getDeclaredMethod("handle", MQEvent.class);
        return ListenerDefinition.builder()
                .bean(new SampleConsumer())
                .method(method)
                .group("sample")
                .namespace("sales")
                .topic("order")
                .tags(tags)
                .supports(List.of("*"))
                .concat(".")
                .build();
    }

    @Test
    void supportsRedisStreamBrokerType() {
        RedisStreamBrokerAdapter adapter = new RedisStreamBrokerAdapter(
                new RedisStreamMQProperties(),
                new MQProperties(),
                new JsonSerialization(),
                mock(UnifiedJedis.class));

        assertTrue(adapter.supports(BrokerType.REDIS_STREAM));
        assertEquals(BrokerType.REDIS_STREAM, adapter.brokerType());
    }

    @Test
    void publisherShouldXaddResolvedStream() {
        UnifiedJedis jedis = mock(UnifiedJedis.class);
        when(jedis.xadd(eq("sales.order.paid"), any(redis.clients.jedis.params.XAddParams.class), any(Map.class)))
                .thenReturn(new StreamEntryID("1-0"));
        MQProperties properties = new MQProperties();
        properties.setNamespace("sales");
        RedisStreamEventPublisher publisher = new RedisStreamEventPublisher(
                jedis,
                properties,
                new JsonSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, Destination.of("order", "paid"));

        verify(jedis).xadd(eq("sales.order.paid"), any(redis.clients.jedis.params.XAddParams.class), any(Map.class));
    }

    @Test
    void consumerRegistrationShouldCreateGroupsForIncludes() throws Exception {
        UnifiedJedis jedis = mock(UnifiedJedis.class);
        RedisStreamMQProperties properties = new RedisStreamMQProperties();
        properties.setAutoStartConsumers(false);
        RedisStreamConsumerEndpointRegistrar registrar = new RedisStreamConsumerEndpointRegistrar(jedis, properties);

        registrar.register(definition("paid || refund"), (message, ack) -> ack.ackSingle());

        verify(jedis).xgroupCreate(eq("sales.order.paid"), eq("sample"), eq(StreamEntryID.XGROUP_LAST_ENTRY), eq(true));
        verify(jedis).xgroupCreate(eq("sales.order.refund"), eq("sample"), eq(StreamEntryID.XGROUP_LAST_ENTRY), eq(true));
    }

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
    void redissonOperationsShouldMapStreamCommands() {
        RedissonClient client = mock(RedissonClient.class);
        RStream<String, String> stream = mock(RStream.class);
        when(client.<String, String>getStream("sales.order.paid")).thenReturn(stream);
        when(stream.add(any(StreamAddArgs.class))).thenReturn(new StreamMessageId(1L, 0L));
        RedissonRedisStreamOperations operations = new RedissonRedisStreamOperations(client);

        String id = operations.add("sales.order.paid", Map.of("payload", "body"));
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

        String id = operations.add("sales.order.paid", Map.of("payload", "body"));
        operations.createGroup("sales.order.paid", "sample");
        operations.ack("sales.order.paid", "sample", "1-0");

        assertEquals("1-0", id);
        verify(commands).xadd(eq("sales.order.paid"), any(XAddArgs.class), anyMap());
        verify(commands).xgroupCreate(any(XReadArgs.StreamOffset.class), eq("sample"), any(XGroupCreateArgs.class));
        verify(commands).xack("sales.order.paid", "sample", "1-0");
    }

    static class SampleConsumer {
        void handle(MQEvent event) {
        }
    }
}
