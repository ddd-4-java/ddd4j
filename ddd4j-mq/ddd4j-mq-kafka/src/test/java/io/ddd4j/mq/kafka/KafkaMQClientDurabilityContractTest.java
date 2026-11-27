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

/**
 * 验证 <code>KafkaMQClientDurabilityContract</code> 在 <code>shouldSeekFailedRecordForRedelivery</code>、<code>shouldSeekRecord</code> 等场景中的行为。
 */
class KafkaMQClientDurabilityContractTest {

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     */
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
     * 验证<code>client.handleRecord(listener, properties, consumer, record)</code> 抛出 <code>KafkaMQClient.BatchRetryException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>client.handleRecord(listener, properties, consumer, record)</code> 抛出 <code>KafkaMQClient.BatchRetryException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>client.handleRecord(listener, properties, consumer, record)</code> 抛出 <code>KafkaMQClient.BatchRetryException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>client.close()</code>、<code>never()</code>，验证该场景。
     */
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
     * 读取 <code>properties</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        return properties;
    }

    /**
     * 构造 <code>MQEventSerialization</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>MQEventSerialization</code> 结果
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

    /**
     * 提供 <code>handle()</code> 等供当前测试使用的独立操作。
     */
    public static final class SuccessfulHandler {
        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
        }
    }

    /**
     * 提供 <code>handle()</code> 等供当前测试使用的独立操作。
     */
    public static final class FailingHandler {
        /**
         * 构造 <code>IllegalStateException</code> 作为当前测试的输入或结果。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            throw new IllegalStateException("handler failed");
        }
    }
}
