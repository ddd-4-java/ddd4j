package io.ddd4j.mq.tdmq.client;

import io.ddd4j.mq.registry.MQTagMatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TDMQ 客户端占位实现：进程内 topic 总线，便于本地联调与契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class TdmqClientPlaceholder implements TdmqClient {

    private final Map<String, CopyOnWriteArrayList<SubscriptionEntry>> topicConsumers = new ConcurrentHashMap<>();
    private final AtomicLong messageSequence = new AtomicLong();

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void publish(String topic, String tag, byte[] payload) {
        log.debug("TDMQ placeholder publish: topic={}, tag={}, size={}",
                topic, tag, Objects.isNull(payload) ? 0 : payload.length);
        List<SubscriptionEntry> entries = topicConsumers.get(topic);
        if (Objects.isNull(entries) || entries.isEmpty()) {
            log.trace("No TDMQ placeholder consumer for topic={}", topic);
            return;
        }
        String messageId = UUID.randomUUID().toString();
        long sequence = messageSequence.incrementAndGet();
        for (SubscriptionEntry entry : entries) {
            if (entry.matches(tag)) {
                entry.getConsumer().onMessage(messageId, String.valueOf(sequence), payload, ack -> {
                    log.trace("TDMQ placeholder ack: topic={}, messageId={}, ack={}", topic, messageId, ack);
                });
            }
        }
    }

    @Override
    public TdmqSubscription subscribe(String topic, String tagExpression, String group, TdmqMessageConsumer consumer) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(consumer, "consumer");
        SubscriptionEntry entry = new SubscriptionEntry(tagExpression, group, consumer);
        topicConsumers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(entry);
        log.info("TDMQ placeholder subscribed: topic={}, tagExpression={}, group={}", topic, tagExpression, group);
        return () -> topicConsumers.computeIfPresent(topic, (key, list) -> {
            list.remove(entry);
            return list.isEmpty() ? null : list;
        });
    }

    @Getter
    private static final class SubscriptionEntry {

        private final String tagExpression;
        private final String group;
        private final TdmqMessageConsumer consumer;

        private SubscriptionEntry(String tagExpression, String group, TdmqMessageConsumer consumer) {
            this.tagExpression = tagExpression;
            this.group = group;
            this.consumer = consumer;
        }

        boolean matches(String messageTag) {
            return MQTagMatcher.match(messageTag, tagExpression);
        }
    }
}
