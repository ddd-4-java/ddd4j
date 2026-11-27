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

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>url != null &amp;&amp; url.startsWith("redis://")</code>为真，<code>entries.size()</code> 等于 <code>3</code>，<code>third.get(MQDeliveryHeaders.CAUSATION_ID)</code> 等于 <code>"causation-2"</code>。
     */
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
     * 读取 <code>event</code> 供当前测试使用。
     *
     * @param topic 传给 <code>event.setTopic()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>event</code>
     */
    private MQEvent event(String topic) {
        MQEvent event = new MQEvent();
        event.setTopic(topic);
        return event;
    }

    /**
     * 验证<code>url != null &amp;&amp; url.startsWith("redis://")</code>为真，<code>handler.received.await(15, TimeUnit.SECONDS)</code>为真，<code>client.acknowledgment.get().correlationId()</code> 等于 <code>"correlation-from-header"</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>url != null &amp;&amp; url.startsWith("redis://")</code>为真，<code>handler.succeeded.await(15, TimeUnit.SECONDS)</code>为真，<code>handler.attempts.get()</code> 等于 <code>2</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>url != null &amp;&amp; url.startsWith("redis://")</code>为真，<code>found</code>为真。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>url != null &amp;&amp; url.startsWith("redis://")</code>为真，<code>firstHandler.delivered.await(5, TimeUnit.SECONDS)</code>为真，<code>observer.xpending(streamKey, secondListener.getGroup()).getTotal()</code> 等于 <code>0</code>。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 将 <code>received</code>、<code>event</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class Handler {
        /**
         * 等待数据到达的同步门闩。
         */
        private final CountDownLatch received = new CountDownLatch(1);
        /**
         * 供测试线程安全保存当前观察结果的引用。
         */
        private final AtomicReference<MQEvent> event = new AtomicReference<>();

        /**
         * 调用 <code>event.set()</code>、<code>received.countDown()</code> 执行当前夹具操作。
         *
         * @param value 当前用例传入的值
         */
        public void handle(MQEvent value) {
            event.set(value);
            received.countDown();
        }
    }

    /**
     * 将 <code>succeeded</code>、<code>attempts</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class RetryHandler {
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final CountDownLatch succeeded = new CountDownLatch(1);
        /**
         * 跟踪测试操作的调用或重试次数。
         */
        private final AtomicInteger attempts = new AtomicInteger();

        /**
         * 调用 <code>attempts.incrementAndGet()</code>、<code>succeeded.countDown()</code> 执行当前夹具操作。
         *
         * @param value 当前用例传入的值
         */
        public void handle(MQEvent value) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("fail first delivery");
            }
            succeeded.countDown();
        }
    }

    /**
     * 将 <code>delivered</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class AlwaysFailHandler {
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final CountDownLatch delivered = new CountDownLatch(1);

        /**
         * 调用 <code>delivered.countDown()</code> 执行当前夹具操作。
         *
         * @param value 当前用例传入的值
         */
        public void handle(MQEvent value) {
            delivered.countDown();
            throw new IllegalStateException("leave delivery pending");
        }
    }

    /**
     * 实现 <code>RedisStreamMQClient</code>，供测试调用 <code>consume()</code> 并观察结果。
     */
    private static final class AckCapturingClient extends RedisStreamMQClient {
        /**
         * 供测试线程安全保存当前观察结果的引用。
         */
        private final AtomicReference<Acknowledgment> acknowledgment = new AtomicReference<>();

        /**
         * 使用 <code>super()</code> 初始化 <code>AckCapturingClient</code> 测试夹具。
         *
         * @param properties 用于构建当前测试运行环境的属性
         */
        private AckCapturingClient(RedisStreamMQProperties properties) {
            super(properties);
        }

        /**
         * 通过<code>acknowledgment.set(ack)</code>、<code>listener.getMethod().invoke(listener.getBean(), event)</code>处理当前调用。
         *
         * @param listener 供当前客户端注册的消息监听器
         * @param event    当前发布、消费或处理的事件
         * @param ack      传给 <code>acknowledgment.set(ack)</code>
         * @throws Throwable 反射访问被测成员时失败
         */
        @Override
        public void consume(MQListener listener, MQEvent event, Acknowledgment ack) throws Throwable {
            acknowledgment.set(ack);
            listener.getMethod().invoke(listener.getBean(), event);
        }
    }
}
