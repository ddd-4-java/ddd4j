package io.ddd4j.mq.kafka;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Kafka 消费组解析和客户端资源所有权的回归契约。 */
class KafkaMQClientLifecycleContractTest {
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
        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        assertThrows(IllegalStateException.class, () -> client.initConsumer(listener(), new MQProperties()));
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
}
