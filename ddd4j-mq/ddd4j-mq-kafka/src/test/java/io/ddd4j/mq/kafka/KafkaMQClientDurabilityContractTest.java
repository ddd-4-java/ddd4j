package io.ddd4j.mq.kafka;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>KafkaMQClientDurabilityContract</code> \u5728 <code>shouldSeekFailedRecordForRedelivery</code>\u3001<code>shouldSeekRecord</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class KafkaMQClientDurabilityContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldPropagateBrokerSendFailureToPublisher() {
        Producer<String, String> producer = mock(Producer.class);
        CompletableFuture<RecordMetadata> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(producer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(failed);
        KafkaMQClient client = new KafkaMQClient(producer, null);
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
    }

    /**
     * \u9a8c\u8bc1<code>client.handleRecord(listener, properties, consumer, record)</code> \u629b\u51fa <code>KafkaMQClient.BatchRetryException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSeekFailedRecordForRedeliveryWithoutCommittingOffset() throws Exception {
        Producer<String, String> producer = mock(Producer.class);
        KafkaMQClient client = new KafkaMQClient(producer, null);
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), null);
        Consumer<String, String> consumer = mock(Consumer.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 2, 17L, "key", "{}");
        FailingHandler handler = new FailingHandler();
        Method method = FailingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(handler, method, method.getAnnotation(MQEventListener.class));

        assertThrows(KafkaMQClient.BatchRetryException.class,
                () -> client.handleRecord(listener, properties, consumer, record));

        verify(consumer).seek(new TopicPartition("orders", 2), 17L);
        verify(consumer, never()).commitSync();
    }

    /**
     * \u9a8c\u8bc1<code>client.handleRecord(listener, properties, consumer, record)</code> \u629b\u51fa <code>KafkaMQClient.BatchRetryException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldSeekRecordWhenOffsetCommitFails() throws Exception {
        KafkaMQClient client = new KafkaMQClient(mock(Producer.class), null);
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), null);
        Consumer<String, String> consumer = mock(Consumer.class);
        doThrow(new IllegalStateException("commit unavailable")).when(consumer).commitSync(anyMap());
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 9L, "key", "{}");
        Method method = SuccessfulHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(new SuccessfulHandler(), method,
                method.getAnnotation(MQEventListener.class));

        assertThrows(KafkaMQClient.BatchRetryException.class,
                () -> client.handleRecord(listener, properties, consumer, record));
        verify(consumer).seek(new TopicPartition("orders", 1), 9L);
    }

    /**
     * \u9a8c\u8bc1<code>client.handleRecord(listener, properties, consumer, record)</code> \u629b\u51fa <code>KafkaMQClient.BatchRetryException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSeekRecordWhenDeserializationFails() throws Exception {
        KafkaMQClient client = new KafkaMQClient(mock(Producer.class), null);
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                throw new IllegalArgumentException("invalid payload");
            }
        }, null);
        Consumer<String, String> consumer = mock(Consumer.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 9L, "key", "bad");
        Method method = FailingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(new FailingHandler(), method,
                method.getAnnotation(MQEventListener.class));

        assertThrows(KafkaMQClient.BatchRetryException.class,
                () -> client.handleRecord(listener, properties, consumer, record));

        verify(consumer).seek(new TopicPartition("orders", 1), 9L);
        verify(consumer, never()).commitSync();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>client.close()</code>\u3001<code>never()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldNotCloseExternallyInjectedProducer() {
        Producer<String, String> producer = mock(Producer.class);
        KafkaMQClient client = new KafkaMQClient(producer, null);

        client.close();
        client.close();

        verify(producer, never()).flush();
        verify(producer, never()).close();
    }

    /**
     * \u8bfb\u53d6 <code>properties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        return properties;
    }

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
     */
    private MQEventSerialization serialization() {
        return new MQEventSerialization() {
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
        };
    }

    /** \u63d0\u4f9b <code>handle()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public static final class SuccessfulHandler {
        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
        }
    }

    /** \u63d0\u4f9b <code>handle()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public static final class FailingHandler {
        /**
         * \u6784\u9020 <code>IllegalStateException</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            throw new IllegalStateException("handler failed");
        }
    }
}
