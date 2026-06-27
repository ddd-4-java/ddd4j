package io.ddd4j.mq.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class KafkaConsumerRunner implements Runnable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final Duration pullTimeout;
    private final Function<ConsumerRecords<String, String>, Void> consumerFunction;

    public KafkaConsumerRunner(KafkaConsumer<String, String> consumer, String topic, Duration pullTimeout, Function<ConsumerRecords<String, String>, Void> consumerFunction) {
        this.consumer = consumer;
        this.topic = topic;
        this.pullTimeout = pullTimeout;
        this.consumerFunction = consumerFunction;
    }

    public void run() {
        try {
            consumer.subscribe(Collections.singletonList(this.topic));
            while (!closed.get()) {
                ConsumerRecords<String, String> records = consumer.poll(pullTimeout);
                // Handle new records
                if (records.count() > 0) {
                    consumerFunction.apply(records);
                }
            }
        } catch (WakeupException e) {
            // Ignore exception if closing
            if (!closed.get()) throw e;
        } finally {
            consumer.close();
        }
    }

    // Shutdown hook which can be called from a separate thread
    public void shutdown() {
        closed.set(true);
        consumer.wakeup();
    }
}