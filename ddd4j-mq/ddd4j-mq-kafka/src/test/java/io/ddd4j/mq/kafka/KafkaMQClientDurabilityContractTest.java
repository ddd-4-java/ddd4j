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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaMQClientDurabilityContractTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

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

        client.handleRecord(listener, properties, consumer, record);

        verify(consumer).seek(new TopicPartition("orders", 2), 17L);
        verify(consumer, never()).commitSync();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSeekRecordWhenDeserializationFails() throws Exception {
        KafkaMQClient client = new KafkaMQClient(mock(Producer.class), null);
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) {
                throw new IllegalArgumentException("invalid payload");
            }
        }, null);
        Consumer<String, String> consumer = mock(Consumer.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 9L, "key", "bad");
        Method method = FailingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(new FailingHandler(), method,
                method.getAnnotation(MQEventListener.class));

        client.handleRecord(listener, properties, consumer, record);

        verify(consumer).seek(new TopicPartition("orders", 1), 9L);
        verify(consumer, never()).commitSync();
    }

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

    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        return properties;
    }

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

    public static final class FailingHandler {
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            throw new IllegalStateException("handler failed");
        }
    }
}
