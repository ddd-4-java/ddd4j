package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.EndpointNaming;
import io.ddd4j.mq.listener.TagMatcher;
import redis.clients.jedis.UnifiedJedis;

import java.util.*;
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

    private static Set<String> resolveStreams(ListenerDefinition definition) {
        Set<String> includes = TagMatcher.findIncludes(definition.getTags());
        Set<String> streams = new LinkedHashSet<>();
        if (includes.isEmpty()) {
            streams.add(stream(definition, null));
        } else {
            includes.forEach(tag -> streams.add(stream(definition, tag)));
        }
        return streams;
    }

    private static String stream(ListenerDefinition definition, String tag) {
        String concat = EndpointNaming.resolveSeparator(definition);
        String base = hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
        return Objects.isNull(tag) ? base : base + concat + tag;
    }

    private static boolean hasText(String s) {
        return Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
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

    private static final class RegisteredConsumer {
        private final RedisStreamOperations operations;
        private final RedisStreamMQProperties properties;
        private final ListenerDefinition definition;
        private final ConsumerHandler handler;
        private final Set<String> streams;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService executor;

        private RegisteredConsumer(
                RedisStreamOperations operations,
                RedisStreamMQProperties properties,
                ListenerDefinition definition,
                ConsumerHandler handler,
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
            if (Objects.nonNull(executor)) {
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
                if (Objects.isNull(records) || records.isEmpty()) {
                    continue;
                }
                records.forEach((stream, entries) -> entries.forEach(entry -> consume(stream, entry)));
            }
        }

        private void consume(String stream, RedisStreamRecord entry) {
            Map<String, String> fields = entry.fields();
            String tag = fields.get(MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, definition.getTags())) {
                return;
            }
            RedisStreamAcknowledgment ack = new RedisStreamAcknowledgment(
                    operations,
                    stream,
                    definition.getGroup(),
                    entry.id(),
                    entry.nativeMessage(),
                    fields.get(MessageHeaders.HEADER_MESSAGE_ID),
                    fields.get(MessageHeaders.HEADER_CORRELATION_ID));
            try {
                MessageConverter<RedisStreamRecord> converter = nativeEntry -> toMessage(stream, nativeEntry, fields);
                handler.handle(converter.convert(entry), ack);
            } catch (Exception ex) {
                ack.nack(true);
            }
        }

        private Message<String> toMessage(String stream, RedisStreamRecord entry, Map<String, String> fields) {
            Map<String, Object> headers = new HashMap<>(fields);
            headers.put(RedisStreamAcknowledgment.HEADER_REDIS_STREAM, stream);
            headers.put(RedisStreamAcknowledgment.HEADER_REDIS_GROUP, definition.getGroup());
            headers.put(RedisStreamAcknowledgment.HEADER_REDIS_ENTRY_ID, entry.id());
            return Message.of(
                    fields.get(RedisStreamMQEventPublisher.FIELD_PAYLOAD),
                    headers,
                    fields.get(MessageHeaders.HEADER_MESSAGE_ID),
                    fields.get(MessageHeaders.HEADER_CORRELATION_ID),
                    entry.nativeMessage());
        }
    }
}
