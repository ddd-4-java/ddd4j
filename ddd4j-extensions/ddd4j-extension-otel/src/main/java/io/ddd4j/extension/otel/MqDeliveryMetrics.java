package io.ddd4j.extension.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可靠消息 Outbox 与 Inbox 的 OTel 指标记录点。
 *
 * <p>指标只携带 broker 和有限结果枚举，刻意不记录 destination、messageId、header、
 * token、authorization 或用户信息，避免高基数和敏感信息泄漏。Exporter、Collector
 * 与告警规则均由宿主应用配置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.0
 */
public final class MqDeliveryMetrics {

    /** Outbox 投递总数，单位为消息。 */
    public static final String OUTBOX_DELIVERY_METRIC = "ddd4j.mq.outbox.delivery";
    /** Inbox 消费结果总数，单位为消息。 */
    public static final String INBOX_DELIVERY_METRIC = "ddd4j.mq.inbox.delivery";

    /** 受控结果标签，避免业务自定义值造成时序数据库基数失控。 */
    public static final AttributeKey<String> ATTR_DELIVERY_OUTCOME =
            AttributeKey.stringKey("ddd4j.delivery.outcome");

    private static final String OUTCOME_PUBLISHED = "published";
    private static final String OUTCOME_RETRY = "retry";
    private static final String OUTCOME_DEAD = "dead";
    private static final String OUTCOME_PROCESSED = "processed";
    private static final String OUTCOME_DUPLICATE = "duplicate";
    private static final String OUTCOME_FAILED = "failed";

    private static final AtomicReference<CounterHandle> OUTBOX_COUNTER = new AtomicReference<>();
    private static final AtomicReference<CounterHandle> INBOX_COUNTER = new AtomicReference<>();

    private MqDeliveryMetrics() {
    }

    /** 记录一条成功确认发布的 Outbox 消息。 */
    public static void outboxPublished(String broker) {
        recordOutbox(broker, OUTCOME_PUBLISHED);
    }

    /** 记录一条等待后续重试的 Outbox 消息。 */
    public static void outboxRetry(String broker) {
        recordOutbox(broker, OUTCOME_RETRY);
    }

    /** 记录一条已进入死信状态的 Outbox 消息。 */
    public static void outboxDead(String broker) {
        recordOutbox(broker, OUTCOME_DEAD);
    }

    /** 记录一条发送或确认失败、最终状态尚未确定的 Outbox 消息。 */
    public static void outboxFailed(String broker) {
        recordOutbox(broker, OUTCOME_FAILED);
    }

    /** 记录一条成功处理的 Inbox 消息。 */
    public static void inboxProcessed(String broker) {
        recordInbox(broker, OUTCOME_PROCESSED);
    }

    /** 记录一条被持久 Inbox 去重的重复消息。 */
    public static void inboxDuplicate(String broker) {
        recordInbox(broker, OUTCOME_DUPLICATE);
    }

    /** 记录一条业务处理失败、应由 broker 重投的 Inbox 消息。 */
    public static void inboxFailed(String broker) {
        recordInbox(broker, OUTCOME_FAILED);
    }

    private static void recordOutbox(String broker, String outcome) {
        counter(OUTBOX_COUNTER, OUTBOX_DELIVERY_METRIC, "Count of ddd4j outbox delivery outcomes")
                .add(1, attributes(broker, outcome));
    }

    private static void recordInbox(String broker, String outcome) {
        counter(INBOX_COUNTER, INBOX_DELIVERY_METRIC, "Count of ddd4j inbox delivery outcomes")
                .add(1, attributes(broker, outcome));
    }

    private static LongCounter counter(AtomicReference<CounterHandle> cache, String name, String description) {
        Meter meter = Ddd4jOtel.meter();
        CounterHandle cached = cache.get();
        if (Objects.nonNull(cached) && cached.meter() == meter) {
            return cached.counter();
        }
        CounterHandle created = new CounterHandle(
                meter,
                meter.counterBuilder(name)
                        .setUnit("{message}")
                        .setDescription(description)
                        .build()
        );
        cache.set(created);
        return created.counter();
    }

    private static Attributes attributes(String broker, String outcome) {
        return Attributes.of(
                Ddd4jOtel.ATTR_MESSAGING_SYSTEM,
                Objects.isNull(broker) ? "unknown" : broker,
                ATTR_DELIVERY_OUTCOME,
                outcome
        );
    }

    private record CounterHandle(Meter meter, LongCounter counter) {
    }
}
