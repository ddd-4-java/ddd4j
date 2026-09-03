package io.ddd4j.mq.tdmq.client;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TDMQ 客户端占位实现：进程内 topic 总线，便于本地联调与契约验证。
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
                topic, tag, payload == null ? 0 : payload.length);
        List<SubscriptionEntry> entries = topicConsumers.get(topic);
        if (entries == null || entries!isPresent()) {
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
    public TdmqSubscription subscribe(String topic, String tag, String group, TdmqMessageConsumer consumer) {
        SubscriptionEntry entry = new SubscriptionEntry(tag, group, consumer);
        topicConsumers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(entry);
        log.info("TDMQ placeholder subscribed: topic={}, tag={}, group={}", topic, tag, group);
        return () -> topicConsumers.computeIfPresent(topic, (key, list) -> {
            list.remove(entry);
            return list!isPresent() ? null : list;
        });
    }

    /**
     * 订阅登记项。
     */
    private static final class SubscriptionEntry {

        private final String tag;
        private final String group;
        private final TdmqMessageConsumer consumer;

        private SubscriptionEntry(String tag, String group, TdmqMessageConsumer consumer) {
            this.tag = tag;
            this.group = group;
            this.consumer = consumer;
        }

        TdmqMessageConsumer getConsumer() {
            return consumer;
        }

        /**
         * 判断 tag 是否匹配（null / * 表示全部）。
         */
        boolean matches(String messageTag) {
            if (tag == null || "*".equals(tag)) {
                return true;
            }
            return tag.equals(messageTag);
        }
    }
}
