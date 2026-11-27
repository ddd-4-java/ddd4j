package io.ddd4j.mq.kafka;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.AbstractExecutorService;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.MockProducer;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.lifecycle.MQStartupState;
import io.ddd4j.mq.listener.MQListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka 消费组解析和客户端资源所有权的回归契约。
 */
class KafkaMQClientLifecycleContractTest {
    /**
     * \u8bfb\u53d6 <code>properties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>properties</code>
     */
    private static KafkaMQProperties properties() {
        KafkaMQProperties properties = new KafkaMQProperties() {
            @Override
            public java.util.Properties producerProperties() {
                java.util.Properties result = super.producerProperties();
                result.put("max.block.ms", "100");
                return result;
            }
        };
        properties.setBootstrapServers("127.0.0.1:1");
        properties.setAutoCreateTopics(false);
        return properties;
    }

    /**
     * \u901a\u8fc7 <code>MQListener.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>MQListener.of(new Handler(), method, method.getAnnotation(MQEventListener.class))</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private static MQListener listener() throws Exception {
        Method method = Handler.class.getMethod("onLifecycleEvent", MQEvent.class);
        return MQListener.of(new Handler(), method, method.getAnnotation(MQEventListener.class));
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>resolve.invoke(client, listener)</code> \u7b49\u4e8e <code>"billing-onLifecycleEvent"</code>\uff0c<code>resolve.invoke(client, listener)</code> \u7b49\u4e8e <code>"explicit"</code>\uff0c<code>resolve.invoke(client, listener)</code> \u7b49\u4e8e <code>"ddd4j-onLifecycleEvent"</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void configuredGroupPrefixIsUsedUnlessListenerHasExplicitGroup() throws Exception {
        KafkaMQProperties properties = properties();
        properties.setGroupIdPrefix("billing");
        KafkaMQClient client = new KafkaMQClient(properties, null);
        MQListener listener = listener();
        Method resolve = KafkaMQClient.class.getDeclaredMethod("buildGroupId", MQListener.class);
        resolve.setAccessible(true);
        assertEquals("billing-onLifecycleEvent", resolve.invoke(client, listener));
        listener.setGroup("explicit");
        assertEquals("explicit", resolve.invoke(client, listener));
        listener.setGroup("");
        properties.setGroupIdPrefix("  ");
        assertEquals("ddd4j-onLifecycleEvent", resolve.invoke(client, listener));
        properties.setGroupIdPrefix(null);
        assertEquals("ddd4j-onLifecycleEvent", resolve.invoke(client, listener));
    }

    /**
     * \u9a8c\u8bc1<code>producer.send(new ProducerRecord&lt;&gt;("orders", "value"))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>client.initProducer(new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void closeReleasesOwnedProducerAndRejectsReinitialization() throws Exception {
        KafkaMQClient client = new KafkaMQClient(properties(), null);
        client.initProducer(new MQProperties());
        Field field = KafkaMQClient.class.getDeclaredField("producer");
        field.setAccessible(true);
        Producer<String, String> producer = (Producer<String, String>) field.get(client);
        try {
            client.close();
            client.close();
            assertThrows(IllegalStateException.class,
                    () -> producer.send(new ProducerRecord<>("orders", "value")));
            assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        } finally {
            producer.close();
        }
    }

    /** \u9a8c\u8bc1<code>producer.closed()</code>\u4e3a\u5047\uff0c<code>producer.history().size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void closeDoesNotCloseExternallyOwnedProducer() {
        MockProducer<String, String> producer =
                new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
        KafkaMQClient client = new KafkaMQClient(producer, null);
        client.close();
        client.close();
        assertFalse(producer.closed());
        producer.send(new ProducerRecord<>("orders", "value"));
        assertEquals(1, producer.history().size());
        producer.close();
    }

    /**
     * \u9a8c\u8bc1<code>ManagementFactory.getPlatformMBeanServer().isRegistered(consumerInfo)</code>\u4e3a\u771f\uff0c<code>workers.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>client.initConsumer(listener(), new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void closeStopsConsumerAndItsExecutorAndRejectsNewConsumers() throws Exception {
        KafkaMQClient client = new KafkaMQClient(properties(), null);
        Set<Thread> before = Thread.getAllStackTraces().keySet();
        client.initConsumer(listener(), new MQProperties());
        ObjectName consumerInfo = new ObjectName("kafka.consumer:type=app-info,id=ddd4j-mq-kafka-consumer");
        assertTrue(ManagementFactory.getPlatformMBeanServer().isRegistered(consumerInfo));
        Set<Thread> workers = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> !before.contains(thread))
                .filter(thread -> thread.getName().equals("ddd4j-kafka-onLifecycleEvent"))
                .collect(Collectors.toSet());
        try {
            assertEquals(1, workers.size(), "one consumer executor must be started");
            client.close();
            client.close();
            assertFalse(ManagementFactory.getPlatformMBeanServer().isRegistered(consumerInfo),
                    "consumer.close must unregister Kafka client metrics");
            for (Thread worker : workers) {
                worker.join(TimeUnit.SECONDS.toMillis(5));
                assertFalse(worker.isAlive(), "consumer executor must terminate on close");
            }
            assertThrows(IllegalStateException.class,
                    () -> client.initConsumer(listener(), new MQProperties()));
        } finally {
            // RED 阶段原实现没有关闭逻辑，避免泄漏测试线程。
            for (Thread worker : workers) {
                worker.interrupt();
                worker.join(TimeUnit.SECONDS.toMillis(2));
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>consumerCreationEntered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>closeEntered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>callers.awaitTermination(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void closeWaitsForInFlightConsumerInitialization() throws Exception {
        CountDownLatch consumerCreationEntered = new CountDownLatch(1);
        CountDownLatch allowConsumerCreation = new CountDownLatch(1);
        CountDownLatch closeEntered = new CountDownLatch(1);
        TrackingConsumer consumer = new TrackingConsumer();
        KafkaMQClient client = new KafkaMQClient(properties(), null) {
            @Override
            org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(java.util.Properties props) {
                consumerCreationEntered.countDown();
                try {
                    if (!allowConsumerCreation.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to finish consumer creation");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return consumer;
            }
        };
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> initialization = callers.submit(
                    () -> client.initConsumer(listener(), new MQProperties()));
            assertTrue(consumerCreationEntered.await(5, TimeUnit.SECONDS));

            Future<?> closing = callers.submit(() -> {
                closeEntered.countDown();
                client.close();
            });
            assertTrue(closeEntered.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> closing.get(200, TimeUnit.MILLISECONDS),
                    "close must wait until the in-flight consumer is registered");

            allowConsumerCreation.countDown();
            assertTrue(initialization.get(5, TimeUnit.SECONDS));
            closing.get(5, TimeUnit.SECONDS);
            assertEquals(1, consumer.closeCount.get());
        } finally {
            allowConsumerCreation.countDown();
            client.close();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.startupStatus().snapshot().state()</code> \u7b49\u4e8e <code>MQStartupState.STOPPED</code>\uff0c<code>client.initConsumer(listener(), new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void closeBeforeInitializationDoesNotCreateResources() throws Exception {
        KafkaMQClient client = new KafkaMQClient(properties(), null);
        client.close();
        client.close();
        assertEquals(MQStartupState.STOPPED, client.startupStatus().snapshot().state());
        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        assertThrows(IllegalStateException.class, () -> client.initConsumer(listener(), new MQProperties()));
    }

    /** \u9a8c\u8bc1<code>producer.closed()</code>\u4e3a\u771f\uff0c<code>holder[0].initProducer(properties)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void ownedProducerCallbackMayCloseClientWithoutDeadlock() {
        AsyncCallbackProducer producer = new AsyncCallbackProducer();
        KafkaMQClient[] holder = new KafkaMQClient[1];
        holder[0] = new KafkaMQClient(properties(), (metadata, exception) -> holder[0].close()) {
            @Override
            Producer<String, String> createProducer(java.util.Properties props) {
                return producer;
            }
        };
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        holder[0].init(Collections.emptyList(), properties,
                new io.ddd4j.mq.serialization.JsonMQEventSerialization(), null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> event.publish());
        assertTrue(producer.closed(), "callback-triggered close must release the owned producer");
        assertThrows(IllegalStateException.class, () -> holder[0].initProducer(properties));
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener(), new MQProperties())</code> \u629b\u51fa <code>RejectedExecutionException.class</code>\uff0c<code>consumer.closeCount.get()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void rejectedConsumerExecutionCleansUpPartialInitialization() throws Exception {
        TrackingConsumer consumer = new TrackingConsumer();
        RejectingExecutor executor = new RejectingExecutor();
        KafkaMQClient client = new KafkaMQClient(properties(), null) {
            @Override
            org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(java.util.Properties props) {
                return consumer;
            }

            @Override
            ExecutorService createConsumerExecutor(MQListener listener) {
                return executor;
            }
        };

        assertThrows(RejectedExecutionException.class,
                () -> client.initConsumer(listener(), new MQProperties()));

        assertTrue(executor.isShutdown(), "rejected worker executor must be shut down");
        assertEquals(1, consumer.closeCount.get(), "partially initialized consumer must be closed once");
        client.close();
        assertEquals(1, consumer.closeCount.get(), "rejected worker must be removed from client ownership");
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener(), new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>consumer.closeCount.get()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void executorCreationFailureClosesAlreadyCreatedConsumer() throws Exception {
        TrackingConsumer consumer = new TrackingConsumer();
        KafkaMQClient client = new KafkaMQClient(properties(), null) {
            @Override
            org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(java.util.Properties props) {
                return consumer;
            }

            @Override
            ExecutorService createConsumerExecutor(MQListener listener) {
                throw new IllegalStateException("executor unavailable");
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.initConsumer(listener(), new MQProperties()));
        assertEquals("executor unavailable", failure.getMessage());
        assertEquals(1, consumer.closeCount.get(),
                "consumer created before executor failure must be released");
        client.close();
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener(), new MQProperties())</code>\u4e3a\u771f\uff0c<code>consumerClosed.await(2, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>consumer.closeCount.get()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void unexpectedPollFailureReleasesConsumerWithoutClientClose() throws Exception {
        CountDownLatch consumerClosed = new CountDownLatch(1);
        TrackingConsumer consumer = new TrackingConsumer() {
            @Override
            public org.apache.kafka.clients.consumer.ConsumerRecords<String, String> poll(java.time.Duration timeout) {
                throw new IllegalStateException("poll failed");
            }

            @Override
            public void close() {
                super.close();
                consumerClosed.countDown();
            }
        };
        KafkaMQClient client = new KafkaMQClient(properties(), null) {
            @Override
            org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(java.util.Properties props) {
                return consumer;
            }
        };
        try {
            assertTrue(client.initConsumer(listener(), new MQProperties()));
            assertTrue(consumerClosed.await(2, TimeUnit.SECONDS),
                    "a failed poll must release the native consumer without waiting for client close");
            assertEquals(1, consumer.closeCount.get());
        } finally {
            client.close();
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.handleRecord(listener(), properties, consumer, record)</code> \u629b\u51fa <code>WakeupException.class</code>\uff0c<code>consumer.seekCalled</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void wakeupDuringCommitEscapesRecordHandlingWithoutSeek() throws Exception {
        MockProducer<String, String> producer =
                new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
        KafkaMQClient client = new KafkaMQClient(producer, null);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        client.init(Collections.emptyList(), properties,
                new MQEventSerialization() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T serialize(Object event) {
                        return (T) "{}";
                    }

                    @Override
                    public <S, T> T deserialize(S value, Class<T> type) {
                        MQEvent event = new MQEvent();
                        event.setTopic("orders");
                        return type.cast(event);
                    }
                }, null);
        WakeupCommitConsumer consumer = new WakeupCommitConsumer();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 3L, "key", "{}");

        assertThrows(WakeupException.class,
                () -> client.handleRecord(listener(), properties, consumer, record));
        assertFalse(consumer.seekCalled, "shutdown wakeup must not be converted into redelivery seek");
        producer.close();
    }

    /** \u63d0\u4f9b <code>onLifecycleEvent()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public static final class Handler {
        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @MQEventListener(topic = "orders")
        public void onLifecycleEvent(MQEvent event) {
        }
    }

    /** \u5b9e\u73b0 <code>MockProducer</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>send()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class AsyncCallbackProducer extends MockProducer<String, String> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>AsyncCallbackProducer</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        private AsyncCallbackProducer() {
            super(true, null, new StringSerializer(), new StringSerializer());
        }

        /**
         * \u8bfb\u53d6 <code>result</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param record \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>ProducerRecord&lt;String, String&gt;</code> \u53c2\u6570 <code>record</code>
         * @param callback \u4f20\u7ed9 <code>callback.onCompletion()</code> \u7684 <code>Callback</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>result</code>
         */
        @Override
        public synchronized Future<RecordMetadata> send(ProducerRecord<String, String> record, Callback callback) {
            CompletableFuture<RecordMetadata> result = new CompletableFuture<>();
            Thread callbackThread = new Thread(() -> {
                callback.onCompletion(null, null);
                result.complete(null);
            }, "kafka-test-callback");
            callbackThread.setDaemon(true);
            callbackThread.start();
            return result;
        }
    }

    /** \u5b9e\u73b0 <code>MockConsumer</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>close()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static class TrackingConsumer extends MockConsumer<String, String> {
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        private final AtomicInteger closeCount = new AtomicInteger();

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TrackingConsumer</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        private TrackingConsumer() {
            super(OffsetResetStrategy.EARLIEST);
        }

        /** \u8c03\u7528 <code>closeCount.incrementAndGet()</code>\u3001<code>super.close()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        @Override
        public void close() {
            closeCount.incrementAndGet();
            super.close();
        }
    }

    /** \u5b9e\u73b0 <code>TrackingConsumer</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>commitSync()</code>\u3001<code>seek()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class WakeupCommitConsumer extends TrackingConsumer {
        /** \u8bb0\u5f55\u6d88\u8d39\u8005\u662f\u5426\u88ab\u8981\u6c42\u91cd\u65b0\u5b9a\u4f4d\u5230\u6307\u5b9a\u504f\u79fb\u3002 */
        private boolean seekCalled;

        /**
         * \u6784\u9020 <code>WakeupException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param offsets \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>java.util.Map&lt;TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata&gt;</code> \u53c2\u6570 <code>offsets</code>
         */
        @Override
        public void commitSync(java.util.Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets) {
            throw new WakeupException();
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>seekCalled = true</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param partition \u4f20\u7ed9 <code>super.seek()</code> \u7684 <code>TopicPartition</code> \u8f93\u5165
         * @param offset \u4f20\u7ed9 <code>super.seek()</code> \u7684 <code>long</code> \u8f93\u5165
         */
        @Override
        public void seek(TopicPartition partition, long offset) {
            seekCalled = true;
            super.seek(partition, offset);
        }
    }

    /** \u5b9e\u73b0 <code>AbstractExecutorService</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>shutdown()</code>\u3001<code>shutdownNow()</code>\u3001<code>isShutdown()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class RejectingExecutor extends AbstractExecutorService {
        /** \u540e\u53f0\u7ec4\u4ef6\u662f\u5426\u5df2\u8fdb\u5165\u5173\u95ed\u8fc7\u7a0b\u3002 */
        private volatile boolean shutdown;

        /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>shutdown = true</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
        @Override
        public void shutdown() {
            shutdown = true;
        }

        /**
         * \u901a\u8fc7 <code>Collections.emptyList()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Collections.emptyList()</code>
         */
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        /**
         * \u8bfb\u53d6\u540e\u53f0\u7ec4\u4ef6\u662f\u5426\u5df2\u8fdb\u5165\u5173\u95ed\u8fc7\u7a0b\u3002
         * @return \u540e\u53f0\u7ec4\u4ef6\u662f\u5426\u5df2\u8fdb\u5165\u5173\u95ed\u8fc7\u7a0b
         */
        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        /**
         * \u8bfb\u53d6\u540e\u53f0\u7ec4\u4ef6\u662f\u5426\u5df2\u8fdb\u5165\u5173\u95ed\u8fc7\u7a0b\u3002
         * @return \u540e\u53f0\u7ec4\u4ef6\u662f\u5426\u5df2\u8fdb\u5165\u5173\u95ed\u8fc7\u7a0b
         */
        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        /**
         * \u8bfb\u53d6\u540e\u53f0\u7ec4\u4ef6\u662f\u5426\u5df2\u8fdb\u5165\u5173\u95ed\u8fc7\u7a0b\u3002
         * @param timeout \u7b49\u5f85\u6d4b\u8bd5\u4e8b\u4ef6\u7684\u8d85\u65f6\u65f6\u957f
         * @param unit \u65f6\u957f\u53c2\u6570\u6240\u4f7f\u7528\u7684\u65f6\u95f4\u5355\u4f4d
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>shutdown</code>
         */
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        /**
         * \u6784\u9020 <code>RejectedExecutionException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param command \u4ea4\u7ed9\u603b\u7ebf\u5904\u7406\u7684\u6d4b\u8bd5\u547d\u4ee4
         */
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("test rejection");
        }
    }
}
