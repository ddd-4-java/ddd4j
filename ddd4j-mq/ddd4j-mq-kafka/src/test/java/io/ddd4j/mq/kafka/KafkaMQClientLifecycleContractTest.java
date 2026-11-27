package io.ddd4j.mq.kafka;

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
     * 读取 <code>properties</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>properties</code>
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
     * 通过 <code>MQListener.of()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>MQListener.of(new Handler(), method, method.getAnnotation(MQEventListener.class))</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private static MQListener listener() throws Exception {
        Method method = Handler.class.getMethod("onLifecycleEvent", MQEvent.class);
        return MQListener.of(new Handler(), method, method.getAnnotation(MQEventListener.class));
    }

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>resolve.invoke(client, listener)</code> 等于 <code>"billing-onLifecycleEvent"</code>，<code>resolve.invoke(client, listener)</code> 等于 <code>"explicit"</code>，<code>resolve.invoke(client, listener)</code> 等于 <code>"ddd4j-onLifecycleEvent"</code>。
     *
     * @throws Exception 反射访问被测成员时失败
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
     * 验证<code>producer.send(new ProducerRecord&lt;&gt;("orders", "value"))</code> 抛出 <code>IllegalStateException.class</code>，<code>client.initProducer(new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     *
     * @throws Exception 反射访问被测成员、发起测试请求时失败
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

    /**
     * 验证<code>producer.closed()</code>为假，<code>producer.history().size()</code> 等于 <code>1</code>。
     */
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
     * 验证<code>ManagementFactory.getPlatformMBeanServer().isRegistered(consumerInfo)</code>为真，<code>workers.size()</code> 等于 <code>1</code>，<code>client.initConsumer(listener(), new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>consumerCreationEntered.await(5, TimeUnit.SECONDS)</code>为真，<code>closeEntered.await(5, TimeUnit.SECONDS)</code>为真，<code>callers.awaitTermination(5, TimeUnit.SECONDS)</code>为真。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>client.startupStatus().snapshot().state()</code> 等于 <code>MQStartupState.STOPPED</code>，<code>client.initConsumer(listener(), new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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

    /**
     * 验证<code>producer.closed()</code>为真，<code>holder[0].initProducer(properties)</code> 抛出 <code>IllegalStateException.class</code>。
     */
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
     * 验证<code>client.initConsumer(listener(), new MQProperties())</code> 抛出 <code>RejectedExecutionException.class</code>，<code>consumer.closeCount.get()</code> 等于 <code>1</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>client.initConsumer(listener(), new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>，<code>consumer.closeCount.get()</code> 等于 <code>1</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>client.initConsumer(listener(), new MQProperties())</code>为真，<code>consumerClosed.await(2, TimeUnit.SECONDS)</code>为真，<code>consumer.closeCount.get()</code> 等于 <code>1</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>client.handleRecord(listener(), properties, consumer, record)</code> 抛出 <code>WakeupException.class</code>，<code>consumer.seekCalled</code>为假。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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

    /**
     * 提供 <code>onLifecycleEvent()</code> 等供当前测试使用的独立操作。
     */
    public static final class Handler {
        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @MQEventListener(topic = "orders")
        public void onLifecycleEvent(MQEvent event) {
        }
    }

    /**
     * 实现 <code>MockProducer</code>，供测试调用 <code>send()</code> 并观察结果。
     */
    private static final class AsyncCallbackProducer extends MockProducer<String, String> {
        /**
         * 使用 <code>super()</code> 初始化 <code>AsyncCallbackProducer</code> 测试夹具。
         */
        private AsyncCallbackProducer() {
            super(true, null, new StringSerializer(), new StringSerializer());
        }

        /**
         * 读取 <code>result</code> 供当前测试使用。
         *
         * @param record   本次调用使用的 <code>ProducerRecord&lt;String, String&gt;</code> 参数 <code>record</code>
         * @param callback 传给 <code>callback.onCompletion()</code> 的 <code>Callback</code> 输入
         * @return 当前测试使用的结果，取自 <code>result</code>
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

    /**
     * 实现 <code>MockConsumer</code>，供测试调用 <code>close()</code> 并观察结果。
     */
    private static class TrackingConsumer extends MockConsumer<String, String> {
        /**
         * 在测试线程之间共享当前操作次数或更新数量的计数器。
         */
        private final AtomicInteger closeCount = new AtomicInteger();

        /**
         * 使用 <code>super()</code> 初始化 <code>TrackingConsumer</code> 测试夹具。
         */
        private TrackingConsumer() {
            super(OffsetResetStrategy.EARLIEST);
        }

        /**
         * 调用 <code>closeCount.incrementAndGet()</code>、<code>super.close()</code> 执行当前夹具操作。
         */
        @Override
        public void close() {
            closeCount.incrementAndGet();
            super.close();
        }
    }

    /**
     * 实现 <code>TrackingConsumer</code>，供测试调用 <code>commitSync()</code>、<code>seek()</code> 并观察结果。
     */
    private static final class WakeupCommitConsumer extends TrackingConsumer {
        /**
         * 记录消费者是否被要求重新定位到指定偏移。
         */
        private boolean seekCalled;

        /**
         * 构造 <code>WakeupException</code> 作为当前测试的输入或结果。
         *
         * @param offsets 本次调用使用的 <code>java.util.Map&lt;TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata&gt;</code> 参数 <code>offsets</code>
         */
        @Override
        public void commitSync(java.util.Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets) {
            throw new WakeupException();
        }

        /**
         * 根据本次调用更新 <code>seekCalled = true</code>，用于后续测试步骤。
         *
         * @param partition 传给 <code>super.seek()</code> 的 <code>TopicPartition</code> 输入
         * @param offset    传给 <code>super.seek()</code> 的 <code>long</code> 输入
         */
        @Override
        public void seek(TopicPartition partition, long offset) {
            seekCalled = true;
            super.seek(partition, offset);
        }
    }

    /**
     * 实现 <code>AbstractExecutorService</code>，供测试调用 <code>shutdown()</code>、<code>shutdownNow()</code>、<code>isShutdown()</code> 并观察结果。
     */
    private static final class RejectingExecutor extends AbstractExecutorService {
        /**
         * 后台组件是否已进入关闭过程。
         */
        private volatile boolean shutdown;

        /**
         * 根据本次调用更新 <code>shutdown = true</code>，用于后续测试步骤。
         */
        @Override
        public void shutdown() {
            shutdown = true;
        }

        /**
         * 通过 <code>Collections.emptyList()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Collections.emptyList()</code>
         */
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        /**
         * 读取后台组件是否已进入关闭过程。
         *
         * @return 后台组件是否已进入关闭过程
         */
        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        /**
         * 读取后台组件是否已进入关闭过程。
         *
         * @return 后台组件是否已进入关闭过程
         */
        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        /**
         * 读取后台组件是否已进入关闭过程。
         *
         * @param timeout 等待测试事件的超时时长
         * @param unit    时长参数所使用的时间单位
         * @return 当前测试使用的结果，取自 <code>shutdown</code>
         */
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        /**
         * 构造 <code>RejectedExecutionException</code> 作为当前测试的输入或结果。
         *
         * @param command 交给总线处理的测试命令
         */
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("test rejection");
        }
    }
}
