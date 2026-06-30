package io.ddd4j.mq.kafka;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.registry.MQTagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Programmatic Kafka consumer registrar.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class KafkaMQConsumerEndpointRegistrar implements AutoCloseable {

    private final KafkaMQProperties kafkaProperties;
    private final List<RegisteredConsumer> registeredConsumers = new CopyOnWriteArrayList<>();

    public KafkaMQConsumerEndpointRegistrar(KafkaMQProperties kafkaProperties) {
        this.kafkaProperties = Objects.requireNonNull(kafkaProperties, "kafkaProperties");
    }

    private static String resolveTopic(MQListenerDefinition definition) {
        String concat = MQListenerEndpointNaming.resolveConcat(definition);
        return hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
    }

    private static MQMessage<String> toMessage(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        Map<String, Object> headers = Map.of(
                KafkaMessageAcknowledgment.HEADER_KAFKA_CONSUMER, consumer,
                KafkaMessageAcknowledgment.HEADER_KAFKA_RECORD, record,
                MQMessages.HEADER_DESTINATION_TOPIC, record.topic(),
                MQMessages.HEADER_DESTINATION_TAG, valueHeader(record, MQMessages.HEADER_DESTINATION_TAG));
        return MQMessage.of(record.value(), headers, valueHeader(record, "messageId"),
                valueHeader(record, "correlationId"), record);
    }

    private static String valueHeader(ConsumerRecord<String, String> record, String key) {
        var header = record.headers().lastHeader(key);
        if (java.util.Objects.isNull(header) || java.util.Objects.isNull(header.value())) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static boolean hasText(String s) {
        return java.util.Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String groupId = resolveGroupId(definition);
        Consumer<String, String> consumer = new KafkaConsumer<>(kafkaProperties.consumerProperties(groupId));
        String topic = resolveTopic(definition);
        consumer.subscribe(List.of(topic));
        RegisteredConsumer registered = new RegisteredConsumer(consumer, definition, handler);
        registeredConsumers.add(registered);
        if (kafkaProperties.isAutoStartConsumers()) {
            registered.start(kafkaProperties);
        }
        log.info("Registered Kafka MQ consumer: topic={}, group={}", topic, groupId);
    }

    @Override
    public void close() {
        registeredConsumers.forEach(RegisteredConsumer::stop);
        registeredConsumers.clear();
    }

    private String resolveGroupId(MQListenerDefinition definition) {
        if (hasText(definition.getGroup())) {
            return definition.getGroup();
        }
        return kafkaProperties.getGroupIdPrefix() + "-" + definition.bindingName();
    }

    private static final class RegisteredConsumer {
        private final Consumer<String, String> consumer;
        private final MQListenerDefinition definition;
        private final MQConsumerHandler handler;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService executor;

        private RegisteredConsumer(
                Consumer<String, String> consumer,
                MQListenerDefinition definition,
                MQConsumerHandler handler) {
            this.consumer = consumer;
            this.definition = definition;
            this.handler = handler;
        }

        private void start(KafkaMQProperties properties) {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ddd4j-kafka-" + definition.bindingName());
                thread.setDaemon(true);
                return thread;
            });
            executor.submit(() -> pollLoop(properties));
        }

        private void stop() {
            running.set(false);
            try {
                consumer.wakeup();
                consumer.close();
            } finally {
                if (java.util.Objects.nonNull(executor)) {
                    executor.shutdownNow();
                }
            }
        }

        private void pollLoop(KafkaMQProperties properties) {
            while (running.get()) {
                try {
                    for (ConsumerRecord<String, String> record : consumer.poll(properties.getPollTimeout())) {
                        if (!MQTagMatcher.match(valueHeader(record, MQMessages.HEADER_DESTINATION_TAG), definition.getTags())) {
                            continue;
                        }
                        KafkaMessageAcknowledgment ack = new KafkaMessageAcknowledgment(consumer, record);
                        handler.handle(toMessage(record, consumer), ack);
                    }
                } catch (Exception ex) {
                    if (running.get()) {
                        log.warn("Kafka MQ consumer poll failed: binding={}", definition.bindingName(), ex);
                    }
                }
            }
        }
    }
}
