package io.ddd4j.mq.redisstream.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 基于 {@link StringRedisTemplate} Redis Stream 的领域事件发布器。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisStreamMQEventPublisher implements MQEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");

        // 逻辑块：补齐事件元数据
        if (!StringUtils.hasText(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StringUtils.hasText(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (event.getMsgId() == null) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        // 逻辑块：序列化并写入 Redis Stream
        String payload = JsonKit.toJson(event);
        String streamKey = buildStreamKey(destination, event.getTag());
        ObjectRecord<String, String> record = StreamRecords.newRecord()
                .ofObject(payload)
                .withStreamKey(streamKey);
        stringRedisTemplate.opsForStream().add(record);
        log.debug("Published Redis Stream event, streamKey={}, msgId={}", streamKey, event.getMsgId());
    }

    /**
     * 根据目的地与 tag 生成 Redis Stream key（namespace:topic[:tag]）。
     */
    private String buildStreamKey(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : properties.getNamespace();
        String topic = StringUtils.hasText(destination.getTopic())
                ? destination.getTopic()
                : properties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.getTag()) ? destination.getTag() : eventTag;
        String base = StringUtils.hasText(namespace) ? namespace + ":" + topic : topic;
        if (!StringUtils.hasText(tag)) {
            return base;
        }
        return base + ":" + tag;
    }
}
