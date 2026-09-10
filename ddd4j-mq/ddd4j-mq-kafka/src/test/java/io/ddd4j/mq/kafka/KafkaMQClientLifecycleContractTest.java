package io.ddd4j.mq.kafka;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.lifecycle.MQStartupState;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Kafka 消费组解析和客户端资源所有权的回归契约。 */
class KafkaMQClientLifecycleContractTest {
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

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

    @Test
    void closeBeforeInitializationDoesNotCreateResources() throws Exception {
        KafkaMQClient client = new KafkaMQClient(properties(), null);
        client.close();
        client.close();
        assertEquals(MQStartupState.STOPPED, client.startupStatus().snapshot().state());
        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        assertThrows(IllegalStateException.class, () -> client.initConsumer(listener(), new MQProperties()));
    }

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

    private static MQListener listener() throws Exception {
        Method method = Handler.class.getMethod("onLifecycleEvent", MQEvent.class);
        return MQListener.of(new Handler(), method, method.getAnnotation(MQEventListener.class));
    }

    public static final class Handler {
        @MQEventListener(topic = "orders")
        public void onLifecycleEvent(MQEvent event) { }
    }

    private static final class AsyncCallbackProducer extends MockProducer<String, String> {
        private AsyncCallbackProducer() {
            super(true, null, new StringSerializer(), new StringSerializer());
        }

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

    private static class TrackingConsumer extends MockConsumer<String, String> {
        private final AtomicInteger closeCount = new AtomicInteger();

        private TrackingConsumer() {
            super(OffsetResetStrategy.EARLIEST);
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            super.close();
        }
    }

    private static final class WakeupCommitConsumer extends TrackingConsumer {
        private boolean seekCalled;

        @Override
        public void commitSync(java.util.Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets) {
            throw new WakeupException();
        }

        @Override
        public void seek(TopicPartition partition, long offset) {
            seekCalled = true;
            super.seek(partition, offset);
        }
    }

    private static final class RejectingExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("test rejection");
        }
    }
}
