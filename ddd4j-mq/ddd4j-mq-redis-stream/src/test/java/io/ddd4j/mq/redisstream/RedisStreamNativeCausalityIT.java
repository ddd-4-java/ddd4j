package io.ddd4j.mq.redisstream;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.message.Acknowledgment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.StreamConsumersInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通过真实 Redis Stream 字段验证跨语言可读取的因果头；需显式指定 ddd4j.redis.native.url。 */
class RedisStreamNativeCausalityIT {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeStreamFieldsCoverAbsentSingleAndBothCausalityIds() {
        String url = System.getProperty("ddd4j.redis.native.url");
        assertTrue(url != null && url.startsWith("redis://"), "需指定隔离 Redis 的 -Dddd4j.redis.native.url");
        RedisStreamMQProperties properties = new RedisStreamMQProperties();
        properties.setUrl(url);
        properties.setEnabled(true);
        properties.setBroker("redisStream");
        properties.setNamespace("ddd4j-native-" + UUID.randomUUID());
        RedisStreamMQClient client = new RedisStreamMQClient(properties);
        try {
            client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
                @Override
                public <T> T serialize(Object event) {
                    return (T) "{}";
                }

                @Override
                public <S, T> T deserialize(S value, Class<T> type) {
                    return null;
                }
            }, null);

            MQEvent absent = event("orders");
            MQEvent single = event("orders");
            single.setCorrelationId("correlation-1");
            MQEvent both = event("orders");
            both.setCorrelationId("correlation-2");
            both.setCausationId("causation-2");
            absent.publish();
            single.publish();
            both.publish();

            String streamKey = client.resolveTopic(absent, properties);
            try (UnifiedJedis observer = properties.newJedis()) {
                List<StreamEntry> entries = observer.xrange(streamKey, "-", "+");
                assertEquals(3, entries.size());
                Map<String, String> first = entries.get(0).getFields();
                Map<String, String> second = entries.get(1).getFields();
                Map<String, String> third = entries.get(2).getFields();
                assertFalse(first.containsKey(MQDeliveryHeaders.CORRELATION_ID));
                assertFalse(first.containsKey(MQDeliveryHeaders.CAUSATION_ID));
                assertEquals("correlation-1", second.get(MQDeliveryHeaders.CORRELATION_ID));
                assertFalse(second.containsKey(MQDeliveryHeaders.CAUSATION_ID));
                assertEquals("correlation-2", third.get(MQDeliveryHeaders.CORRELATION_ID));
                assertEquals("causation-2", third.get(MQDeliveryHeaders.CAUSATION_ID));
                observer.del(streamKey);
            }
        } finally {
            client.close();
        }
    }

    private MQEvent event(String topic) {
        MQEvent event = new MQEvent();
        event.setTopic(topic);
        return event;
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeConsumerRestoresCausalityFromStreamFields() throws Exception {
        String url = System.getProperty("ddd4j.redis.native.url");
        assertTrue(url != null && url.startsWith("redis://"), "需指定隔离 Redis 的 -Dddd4j.redis.native.url");
        RedisStreamMQProperties properties = new RedisStreamMQProperties();
        properties.setUrl(url);
        properties.setEnabled(true);
        properties.setBroker("redisStream");
        properties.setNamespace("ddd4j-native-" + UUID.randomUUID());
        Handler handler = new Handler();
        MQListener listener = MQListener.builder()
                .bean(handler)
                .method(Handler.class.getMethod("handle", MQEvent.class))
                .group("ddd4j-native-group")
                .topic("orders")
                .tags("*")
                .supports(Collections.singletonList("*"))
                .build();
        AckCapturingClient client = new AckCapturingClient(properties);
        try {
            client.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
                @Override
                public <T> T serialize(Object event) {
                    return (T) "real";
                }

                @Override
                public <S, T> T deserialize(S value, Class<T> type) {
                    return "real".equals(value) ? (T) new MQEvent() : null;
                }
            }, null);
            MQEvent event = event("orders");
            event.setCorrelationId("correlation-from-header");
            event.setCausationId("causation-from-header");
            event.publish();

            assertTrue(handler.received.await(15, TimeUnit.SECONDS), "Redis Stream consumer did not receive event");
            assertEquals("correlation-from-header", handler.event.get().getCorrelationId());
            assertEquals("causation-from-header", handler.event.get().getCausationId());
            assertEquals("correlation-from-header", client.acknowledgment.get().correlationId());
        } finally {
            client.close();
        }
    }

    public static final class Handler {
        private final CountDownLatch received = new CountDownLatch(1);
        private final AtomicReference<MQEvent> event = new AtomicReference<>();

        public void handle(MQEvent value) {
            event.set(value);
            received.countDown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedDeliveryRemainsPendingUntilSuccessfulRetry() throws Exception {
        String url = System.getProperty("ddd4j.redis.native.url");
        assertTrue(url != null && url.startsWith("redis://"), "需指定隔离 Redis 的 -Dddd4j.redis.native.url");
        RedisStreamMQProperties properties = new RedisStreamMQProperties();
        properties.setUrl(url);
        properties.setEnabled(true);
        properties.setBroker("redisStream");
        properties.setNamespace("ddd4j-native-" + UUID.randomUUID());
        RetryHandler handler = new RetryHandler();
        MQListener listener = MQListener.builder()
                .bean(handler)
                .method(RetryHandler.class.getMethod("handle", MQEvent.class))
                .group("ddd4j-native-retry")
                .topic("orders")
                .tags("*")
                .supports(Collections.singletonList("*"))
                .build();
        RedisStreamMQClient client = new RedisStreamMQClient(properties);
        try {
            client.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
                @Override
                public <T> T serialize(Object event) {
                    return (T) "real";
                }

                @Override
                public <S, T> T deserialize(S value, Class<T> type) {
                    return "real".equals(value) ? (T) new MQEvent() : null;
                }
            }, null);
            MQEvent event = event("orders");
            event.publish();

            assertTrue(handler.succeeded.await(15, TimeUnit.SECONDS), "failed Redis delivery was not retried");
            String streamKey = client.resolveTopic(event, properties);
            try (UnifiedJedis observer = properties.newJedis()) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (observer.xpending(streamKey, listener.getGroup()).getTotal() != 0
                        && System.nanoTime() < deadline) {
                    TimeUnit.MILLISECONDS.sleep(20);
                }
                assertEquals(0, observer.xpending(streamKey, listener.getGroup()).getTotal());
            }
            assertEquals(2, handler.attempts.get());
        } finally {
            client.close();
            client.close();
        }
    }

    public static final class RetryHandler {
        private final CountDownLatch succeeded = new CountDownLatch(1);
        private final AtomicInteger attempts = new AtomicInteger();

        public void handle(MQEvent value) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("fail first delivery");
            }
            succeeded.countDown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuredConsumerNameIsVisibleInNativeConsumerGroup() throws Exception {
        String url = System.getProperty("ddd4j.redis.native.url");
        assertTrue(url != null && url.startsWith("redis://"), "需指定隔离 Redis 的 -Dddd4j.redis.native.url");
        RedisStreamMQProperties properties = new RedisStreamMQProperties();
        properties.setUrl(url);
        properties.setEnabled(true);
        properties.setBroker("redisStream");
        properties.setNamespace("ddd4j-native-" + UUID.randomUUID());
        properties.setConsumerName("worker-a");
        Handler handler = new Handler();
        MQListener listener = MQListener.builder()
                .bean(handler)
                .method(Handler.class.getMethod("handle", MQEvent.class))
                .group("ddd4j-native-consumer-name")
                .topic("orders")
                .tags("*")
                .supports(Collections.singletonList("*"))
                .build();
        RedisStreamMQClient client = new RedisStreamMQClient(properties);
        try {
            client.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
                @Override
                public <T> T serialize(Object event) {
                    return (T) "{}";
                }

                @Override
                public <S, T> T deserialize(S value, Class<T> type) {
                    return null;
                }
            }, null);
            String streamKey = client.resolveTopic(listener, properties);
            try (UnifiedJedis observer = properties.newJedis()) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean found = false;
                while (!found && System.nanoTime() < deadline) {
                    List<StreamConsumersInfo> consumers = observer.xinfoConsumers(streamKey, listener.getGroup());
                    found = consumers.stream().anyMatch(info -> info.getName().startsWith("worker-a-"));
                    if (!found) {
                        TimeUnit.MILLISECONDS.sleep(20);
                    }
                }
                assertTrue(found, "configured consumerName must identify the native Redis consumer");
            }
        } finally {
            client.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void anotherConsumerCanClaimIdlePendingDelivery() throws Exception {
        String url = System.getProperty("ddd4j.redis.native.url");
        assertTrue(url != null && url.startsWith("redis://"), "需指定隔离 Redis 的 -Dddd4j.redis.native.url");
        String namespace = "ddd4j-native-" + UUID.randomUUID();
        RedisStreamMQProperties firstProperties = new RedisStreamMQProperties();
        firstProperties.setUrl(url);
        firstProperties.setEnabled(true);
        firstProperties.setBroker("redisStream");
        firstProperties.setNamespace(namespace);
        firstProperties.setConsumerName("worker-a");
        RedisStreamMQProperties secondProperties = new RedisStreamMQProperties();
        secondProperties.setUrl(url);
        secondProperties.setEnabled(true);
        secondProperties.setBroker("redisStream");
        secondProperties.setNamespace(namespace);
        secondProperties.setConsumerName("worker-b");
        secondProperties.setAutoClaimEnabled(true);
        secondProperties.setClaimMinIdleMillis(100);
        AlwaysFailHandler firstHandler = new AlwaysFailHandler();
        Handler secondHandler = new Handler();
        MQListener firstListener = MQListener.builder()
                .bean(firstHandler)
                .method(AlwaysFailHandler.class.getMethod("handle", MQEvent.class))
                .group("ddd4j-native-takeover")
                .topic("orders")
                .tags("*")
                .supports(Collections.singletonList("*"))
                .build();
        MQListener secondListener = MQListener.builder()
                .bean(secondHandler)
                .method(Handler.class.getMethod("handle", MQEvent.class))
                .group("ddd4j-native-takeover")
                .topic("orders")
                .tags("*")
                .supports(Collections.singletonList("*"))
                .build();
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override
            public <T> T serialize(Object event) {
                return (T) "real";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return "real".equals(value) ? (T) new MQEvent() : null;
            }
        };
        RedisStreamMQClient first = new RedisStreamMQClient(firstProperties);
        RedisStreamMQClient second = new RedisStreamMQClient(secondProperties);
        try {
            first.init(Collections.singletonList(firstListener), firstProperties, serialization, null);
            MQEvent event = event("orders");
            event.publish();
            assertTrue(firstHandler.delivered.await(5, TimeUnit.SECONDS));
            first.close();

            second.init(Collections.singletonList(secondListener), secondProperties, serialization, null);
            assertTrue(secondHandler.received.await(8, TimeUnit.SECONDS),
                    "worker-b did not claim worker-a's pending delivery");
            String streamKey = second.resolveTopic(event, secondProperties);
            try (UnifiedJedis observer = secondProperties.newJedis()) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (observer.xpending(streamKey, secondListener.getGroup()).getTotal() != 0
                        && System.nanoTime() < deadline) {
                    TimeUnit.MILLISECONDS.sleep(20);
                }
                assertEquals(0, observer.xpending(streamKey, secondListener.getGroup()).getTotal());
            }
        } finally {
            first.close();
            second.close();
        }
    }

    public static final class AlwaysFailHandler {
        private final CountDownLatch delivered = new CountDownLatch(1);

        public void handle(MQEvent value) {
            delivered.countDown();
            throw new IllegalStateException("leave delivery pending");
        }
    }

    private static final class AckCapturingClient extends RedisStreamMQClient {
        private final AtomicReference<Acknowledgment> acknowledgment = new AtomicReference<>();

        private AckCapturingClient(RedisStreamMQProperties properties) {
            super(properties);
        }

        @Override
        public void consume(MQListener listener, MQEvent event, Acknowledgment ack) throws Throwable {
            acknowledgment.set(ack);
            listener.getMethod().invoke(listener.getBean(), event);
        }
    }
}
