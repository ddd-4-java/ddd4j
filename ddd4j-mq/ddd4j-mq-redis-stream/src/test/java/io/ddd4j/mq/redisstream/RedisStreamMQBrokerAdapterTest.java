package io.ddd4j.mq.redisstream;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.XAddParams;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Redis Stream adapter contract tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RedisStreamMQBrokerAdapterTest {

    @Test
    void supportsRedisStreamBrokerType() {
        RedisStreamMQBrokerAdapter adapter = new RedisStreamMQBrokerAdapter(
                new RedisStreamMQProperties(),
                new Ddd4jMQProperties(),
                new JsonMQMessageSerialization(),
                mock(UnifiedJedis.class));

        assertTrue(adapter.supports(MQBrokerType.REDIS_STREAM));
        assertEquals(MQBrokerType.REDIS_STREAM, adapter.brokerType());
    }

    @Test
    void publisherShouldXaddResolvedStream() {
        UnifiedJedis jedis = mock(UnifiedJedis.class);
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setNamespace("sales");
        RedisStreamMQEventPublisher publisher = new RedisStreamMQEventPublisher(
                jedis,
                properties,
                new JsonMQMessageSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, MQDestination.of("order", "paid"));

        verify(jedis).xadd(eq("sales.order.paid"), any(XAddParams.class), any(Map.class));
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
        RedisStreamMessageAcknowledgment ack = new RedisStreamMessageAcknowledgment(
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

    private static MQListenerDefinition definition(String tags) throws Exception {
        Method method = SampleConsumer.class.getDeclaredMethod("handle", MQEvent.class);
        return MQListenerDefinition.builder()
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

    static class SampleConsumer {
        void handle(MQEvent event) {
        }
    }
}
