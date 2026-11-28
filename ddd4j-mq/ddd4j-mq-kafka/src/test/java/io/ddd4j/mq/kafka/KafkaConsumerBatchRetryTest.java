package io.ddd4j.mq.kafka;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 多分区批次重试必须保留未处理消息，且同一分区不能越过失败记录。
 */
class KafkaConsumerBatchRetryTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void shouldProcessEveryPartitionInOrderAfterOneRecordFails() throws Exception {
        MockConsumer<String, String> consumer = new MockConsumer<>("earliest");
        ExecutorService executor = mock(ExecutorService.class);
        KafkaMQClient client = new KafkaMQClient(new KafkaMQProperties(), null) {
            @Override
            public Consumer<MQEvent> initProducer(MQProperties properties) {
                return null;
            }

            @Override
            org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(Properties properties) {
                return consumer;
            }

            @Override
            ExecutorService createConsumerExecutor(MQListener listener) {
                return executor;
            }
        };
        RetryHandler handler = new RetryHandler();
        Method method = RetryHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(handler, method, method.getAnnotation(MQEventListener.class));
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        TopicPartition first = new TopicPartition("orders", 0);
        TopicPartition second = new TopicPartition("orders", 1);
        Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();

        consumer.schedulePollTask(() -> {
            consumer.rebalance(List.of(first, second));
            consumer.updateBeginningOffsets(Map.of(first, 0L, second, 0L));
            addRecords(consumer, 2);
        });
        // MockConsumer 按当前 position 过滤这些记录，模拟 broker 重投以及后续新消息。
        consumer.schedulePollTask(() -> addRecords(consumer, 3));
        consumer.schedulePollTask(() -> {
            committed.putAll(consumer.committed(Set.of(first, second)));
            client.close();
        });

        try {
            client.init(List.of(listener), properties, serialization(), null);
            ArgumentCaptor<Runnable> worker = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(worker.capture());
            worker.getValue().run();

            assertEquals(List.of("0:0", "0:1", "0:2"), handler.handled.stream()
                    .filter(id -> id.startsWith("0:")).toList());
            assertEquals(List.of("1:0", "1:1", "1:2"), handler.handled.stream()
                    .filter(id -> id.startsWith("1:")).toList());
            assertEquals(3L, committed.get(first).offset());
            assertEquals(3L, committed.get(second).offset());
        } finally {
            client.close();
        }
    }

    /**
     * 向两个分区提供相同长度的事件日志。
     */
    private static void addRecords(MockConsumer<String, String> consumer, int count) {
        for (int partition = 0; partition < 2; partition++) {
            for (int offset = 0; offset < count; offset++) {
                consumer.addRecord(new ConsumerRecord<>("orders", partition, offset,
                        "key", partition + ":" + offset));
            }
        }
    }

    private static MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object value) {
                return (T) value.toString();
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                MQEvent event = new MQEvent();
                event.setTopic("orders");
                event.setMsgId(value.toString());
                return type.cast(event);
            }
        };
    }

    /**
     * 首次消费失败，后续记录业务实际完成的顺序。
     */
    public static final class RetryHandler {
        private boolean failed;
        private final List<String> handled = new ArrayList<>();

        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            if (!failed) {
                failed = true;
                throw new IllegalStateException("retry first delivery");
            }
            handled.add(event.getMsgId());
        }
    }
}
