package io.ddd4j.mq.redisstream;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.resps.StreamConsumersInfo;
import redis.clients.jedis.resps.StreamEntry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过真实 Redis Stream 字段验证跨语言可读取的因果头；需显式指定 ddd4j.redis.native.url。
 */
class RedisStreamNativeCausalityIT {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>url != null &amp;&amp; url.startsWith("redis://")</code>\u4e3a\u771f\uff0c<code>entries.size()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>third.get(MQDeliveryHeaders.CAUSATION_ID)</code> \u7b49\u4e8e <code>"causation-2"</code>\u3002 */
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

    /**
     * \u8bfb\u53d6 <code>event</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param topic \u4f20\u7ed9 <code>event.setTopic()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>event</code>
     */
    private MQEvent event(String topic) {
        MQEvent event = new MQEvent();
        event.setTopic(topic);
        return event;
    }

    /**
     * \u9a8c\u8bc1<code>url != null &amp;&amp; url.startsWith("redis://")</code>\u4e3a\u771f\uff0c<code>handler.received.await(15, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>client.acknowledgment.get().correlationId()</code> \u7b49\u4e8e <code>"correlation-from-header"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>url != null &amp;&amp; url.startsWith("redis://")</code>\u4e3a\u771f\uff0c<code>handler.succeeded.await(15, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>handler.attempts.get()</code> \u7b49\u4e8e <code>2</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>url != null &amp;&amp; url.startsWith("redis://")</code>\u4e3a\u771f\uff0c<code>found</code>\u4e3a\u771f\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>url != null &amp;&amp; url.startsWith("redis://")</code>\u4e3a\u771f\uff0c<code>firstHandler.delivered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>observer.xpending(streamKey, secondListener.getGroup()).getTotal()</code> \u7b49\u4e8e <code>0</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /** \u5c06 <code>received</code>\u3001<code>event</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class Handler {
        /** \u7b49\u5f85\u6570\u636e\u5230\u8fbe\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch received = new CountDownLatch(1);
        /** \u4f9b\u6d4b\u8bd5\u7ebf\u7a0b\u5b89\u5168\u4fdd\u5b58\u5f53\u524d\u89c2\u5bdf\u7ed3\u679c\u7684\u5f15\u7528\u3002 */
        private final AtomicReference<MQEvent> event = new AtomicReference<>();

        /**
         * \u8c03\u7528 <code>event.set()</code>\u3001<code>received.countDown()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        public void handle(MQEvent value) {
            event.set(value);
            received.countDown();
        }
    }

    /** \u5c06 <code>succeeded</code>\u3001<code>attempts</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class RetryHandler {
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch succeeded = new CountDownLatch(1);
        /** \u8ddf\u8e2a\u6d4b\u8bd5\u64cd\u4f5c\u7684\u8c03\u7528\u6216\u91cd\u8bd5\u6b21\u6570\u3002 */
        private final AtomicInteger attempts = new AtomicInteger();

        /**
         * \u8c03\u7528 <code>attempts.incrementAndGet()</code>\u3001<code>succeeded.countDown()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        public void handle(MQEvent value) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("fail first delivery");
            }
            succeeded.countDown();
        }
    }

    /** \u5c06 <code>delivered</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class AlwaysFailHandler {
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch delivered = new CountDownLatch(1);

        /**
         * \u8c03\u7528 <code>delivered.countDown()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        public void handle(MQEvent value) {
            delivered.countDown();
            throw new IllegalStateException("leave delivery pending");
        }
    }

    /** \u5b9e\u73b0 <code>RedisStreamMQClient</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>consume()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class AckCapturingClient extends RedisStreamMQClient {
        /** \u4f9b\u6d4b\u8bd5\u7ebf\u7a0b\u5b89\u5168\u4fdd\u5b58\u5f53\u524d\u89c2\u5bdf\u7ed3\u679c\u7684\u5f15\u7528\u3002 */
        private final AtomicReference<Acknowledgment> acknowledgment = new AtomicReference<>();

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>AckCapturingClient</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param properties \u7528\u4e8e\u6784\u5efa\u5f53\u524d\u6d4b\u8bd5\u8fd0\u884c\u73af\u5883\u7684\u5c5e\u6027
         */
        private AckCapturingClient(RedisStreamMQProperties properties) {
            super(properties);
        }

        /**
         * 通过<code>acknowledgment.set(ack)</code>、<code>listener.getMethod().invoke(listener.getBean(), event)</code>处理当前调用。
         *
         * @param listener 供当前客户端注册的消息监听器
         * @param event 当前发布、消费或处理的事件
         * @param ack 传给 <code>acknowledgment.set(ack)</code>
         * @throws Throwable 反射访问被测成员时失败
         */
        @Override
        public void consume(MQListener listener, MQEvent event, Acknowledgment ack) throws Throwable {
            acknowledgment.set(ack);
            listener.getMethod().invoke(listener.getBean(), event);
        }
    }
}
