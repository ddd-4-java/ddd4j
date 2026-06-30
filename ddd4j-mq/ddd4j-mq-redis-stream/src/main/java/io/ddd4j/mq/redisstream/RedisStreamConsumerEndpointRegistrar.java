package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.registry.MQTagMatcher;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import redis.clients.jedis.UnifiedJedis;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Programmatic Redis Stream consumer registrar.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamConsumerEndpointRegistrar implements AutoCloseable {

    private final RedisStreamOperations operations;
    private final RedisStreamMQProperties redisProperties;
    private final List<RegisteredConsumer> consumers = new CopyOnWriteArrayList<>();

    public RedisStreamConsumerEndpointRegistrar(UnifiedJedis jedis, RedisStreamMQProperties redisProperties) {
        this(new JedisRedisStreamOperations(jedis), redisProperties);
    }

    public RedisStreamConsumerEndpointRegistrar(RedisStreamOperations operations, RedisStreamMQProperties redisProperties) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Set<String> streams = resolveStreams(definition);
        if (redisProperties.isAutoCreateGroup()) {
            streams.forEach(stream -> createGroupIfNecessary(stream, definition.getGroup()));
        }
        RegisteredConsumer consumer = new RegisteredConsumer(operations, redisProperties, definition, handler, streams);
        consumers.add(consumer);
        if (redisProperties.isAutoStartConsumers()) {
            consumer.start();
        }
    }

    @Override
    public void close() {
        consumers.forEach(RegisteredConsumer::stop);
        consumers.clear();
    }

    private void createGroupIfNecessary(String stream, String group) {
        operations.createGroup(stream, group);
    }

    private static Set<String> resolveStreams(MQListenerDefinition definition) {
        Set<String> includes = MQTagMatcher.findIncludes(definition.getTags());
        Set<String> streams = new LinkedHashSet<>();
        if (includes.isEmpty()) {
            streams.add(stream(definition, null));
        } else {
            includes.forEach(tag -> streams.add(stream(definition, tag)));
        }
        return streams;
    }

    private static String stream(MQListenerDefinition definition, String tag) {
        String concat = MQListenerEndpointNaming.resolveConcat(definition);
        String base = hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
        return tag == null ? base : base + concat + tag;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static final class RegisteredConsumer {
        private final RedisStreamOperations operations;
        private final RedisStreamMQProperties properties;
        private final MQListenerDefinition definition;
        private final MQConsumerHandler handler;
        private final Set<String> streams;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService executor;

        private RegisteredConsumer(
                RedisStreamOperations operations,
                RedisStreamMQProperties properties,
                MQListenerDefinition definition,
                MQConsumerHandler handler,
                Set<String> streams) {
            this.operations = operations;
            this.properties = properties;
            this.definition = definition;
            this.handler = handler;
            this.streams = streams;
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ddd4j-redis-stream-" + definition.bindingName());
                thread.setDaemon(true);
                return thread;
            });
            executor.submit(this::pollLoop);
        }

        private void stop() {
            running.set(false);
            if (executor != null) {
                executor.shutdownNow();
            }
        }

        private void pollLoop() {
            while (running.get()) {
                Map<String, List<RedisStreamRecord>> records = operations.readGroup(
                        definition.getGroup(),
                        properties.getConsumerName(),
                        properties.getCount(),
                        properties.getBlockMillis(),
                        streams);
                if (records == null || records.isEmpty()) {
                    continue;
                }
                records.forEach((stream, entries) -> entries.forEach(entry -> consume(stream, entry)));
            }
        }

        private void consume(String stream, RedisStreamRecord entry) {
            Map<String, String> fields = entry.fields();
            String tag = fields.get(MQMessages.HEADER_DESTINATION_TAG);
            if (!MQTagMatcher.match(tag, definition.getTags())) {
                return;
            }
            RedisStreamMessageAcknowledgment ack = new RedisStreamMessageAcknowledgment(
                    operations,
                    stream,
                    definition.getGroup(),
                    entry.id(),
                    entry.nativeMessage(),
                    fields.get(MQMessages.HEADER_MESSAGE_ID),
                    fields.get(MQMessages.HEADER_CORRELATION_ID));
            try {
                handler.handle(toMessage(stream, entry, fields), ack);
            } catch (Exception ex) {
                ack.nack(true);
            }
        }

        private MQMessage<String> toMessage(String stream, RedisStreamRecord entry, Map<String, String> fields) {
            Map<String, Object> headers = new HashMap<>(fields);
            headers.put(RedisStreamMessageAcknowledgment.HEADER_REDIS_STREAM, stream);
            headers.put(RedisStreamMessageAcknowledgment.HEADER_REDIS_GROUP, definition.getGroup());
            headers.put(RedisStreamMessageAcknowledgment.HEADER_REDIS_ENTRY_ID, entry.id());
            return MQMessage.of(
                    fields.get(RedisStreamMQEventPublisher.FIELD_PAYLOAD),
                    headers,
                    fields.get(MQMessages.HEADER_MESSAGE_ID),
                    fields.get(MQMessages.HEADER_CORRELATION_ID),
                    entry.nativeMessage());
        }
    }
}
