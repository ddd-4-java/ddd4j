package io.ddd4j.mq.redisstream.consumer;

import io.ddd4j.mq.redisstream.ack.RedisStreamMessageAcknowledgment;
import io.ddd4j.mq.redisstream.ack.RedisStreamMessageAcknowledgmentFactory;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 动态注册为 Redis Stream {@link StreamMessageListenerContainer} 消费端点。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class RedisStreamConsumerEndpointRegistrar implements AutoCloseable {

    private final ApplicationContext applicationContext;
    private final Ddd4jMQProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    /**
     * 注册单个监听器定义。
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        ensureContainerStarted();
        String streamKey = buildStreamKey(definition);
        String consumerGroup = definition.getGroup();
        String consumerName = buildConsumerName(definition);

        // 逻辑块：确保 stream 与消费者组存在
        try {
            stringRedisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroup);
        } catch (Exception ex) {
            log.debug("Redis stream group may already exist: stream={}, group={}, cause={}",
                    streamKey, consumerGroup, ex.getMessage());
        }

        Consumer redisConsumer = Consumer.from(consumerGroup, consumerName);
        StreamOffset<String> offset = StreamOffset.create(streamKey, ReadOffset.lastConsumed());
        Subscription subscription = container.receive(redisConsumer, offset, record ->
                onRecord(record, definition, handler, streamKey, consumerGroup));
        subscriptions.add(subscription);
        registeredDefinitions.add(definition);

        log.info("Registered Redis Stream listener: stream={}, group={}, consumer={}",
                streamKey, consumerGroup, consumerName);
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("Redis Stream consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        for (Subscription subscription : subscriptions) {
            try {
                subscription.cancel();
            } catch (Exception ex) {
                log.warn("Failed to cancel Redis Stream subscription", ex);
            }
        }
        subscriptions.clear();
        if (container != null) {
            container.stop();
            container = null;
        }
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 懒启动 StreamMessageListenerContainer。
     */
    private void ensureContainerStarted() {
        if (container != null) {
            return;
        }
        RedisConnectionFactory connectionFactory = stringRedisTemplate.getConnectionFactory();
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
    }

    /**
     * 处理 Stream 记录并委托 {@link MQConsumerHandler}。
     */
    private void onRecord(
            MapRecord<String, String, String> record,
            MQListenerDefinition definition,
            MQConsumerHandler handler,
            String streamKey,
            String consumerGroup) {

        try {
            String payloadText = record.getValue().getOrDefault("payload", record.toString());
            if (record.getValue().containsKey("data")) {
                payloadText = record.getValue().get("data");
            }
            org.springframework.messaging.Message<String> springMessage = MessageBuilder
                    .withPayload(payloadText)
                    .setHeader(RedisStreamMessageAcknowledgment.HEADER_STREAM_KEY, streamKey)
                    .setHeader(RedisStreamMessageAcknowledgment.HEADER_CONSUMER_GROUP, consumerGroup)
                    .setHeader(RedisStreamMessageAcknowledgment.HEADER_RECORD_ID, record.getId())
                    .build();

            Map<String, Object> headers = new HashMap<>(springMessage.getHeaders());
            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    record.getId().getValue(),
                    null,
                    springMessage);

            MessageAcknowledgment ack = RedisStreamMessageAcknowledgmentFactory
                    .fromSpringMessage(springMessage, stringRedisTemplate)
                    .map(a -> (MessageAcknowledgment) a)
                    .orElseGet(NoOpMessageAcknowledgment::new);
            handler.handle(mqMessage, ack);
        } catch (Exception ex) {
            log.error("Redis Stream consumer failed: bean={}, method={}",
                    definition.getBean().getClass().getSimpleName(),
                    definition.getMethod().getName(),
                    ex);
        }
    }

    private String buildStreamKey(MQListenerDefinition definition) {
        String concat = StringUtils.hasText(definition.getConcat()) ? definition.getConcat() : ".";
        String namespace = StringUtils.hasText(definition.getNamespace())
                ? definition.getNamespace()
                : properties.getNamespace();
        String topic = definition.getTopic();
        String tags = definition.getTags();
        String base = namespace + concat + topic;
        if (StringUtils.hasText(tags) && !"*".equals(tags.trim())) {
            String tag = tags.contains("||") ? tags.substring(0, tags.indexOf("||")).trim() : tags.trim();
            return base + concat + tag;
        }
        return base;
    }

    private String buildConsumerName(MQListenerDefinition definition) {
        return "ddd4j-" + definition.bindingName() + "-"
                + definition.getMethod().getDeclaringClass().getSimpleName();
    }
}
