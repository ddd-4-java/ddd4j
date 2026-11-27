/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /**
     * Outbox 投递总数，单位为消息。
     */
    public static final String OUTBOX_DELIVERY_METRIC = "ddd4j.mq.outbox.delivery";
    /**
     * Inbox 消费结果总数，单位为消息。
     */
    public static final String INBOX_DELIVERY_METRIC = "ddd4j.mq.inbox.delivery";

    /**
     * 受控结果标签，避免业务自定义值造成时序数据库基数失控。
     */
    public static final AttributeKey<String> ATTR_DELIVERY_OUTCOME =
            AttributeKey.stringKey("ddd4j.delivery.outcome");

    /** \u8bb0\u5f55 Outbox \u6210\u529f\u53d1\u5e03\u7684\u6307\u6807\u7ed3\u679c\u6807\u7b7e\u3002 */
    private static final String OUTCOME_PUBLISHED = "published";
    /** \u8bb0\u5f55 Outbox \u91cd\u65b0\u5b89\u6392\u91cd\u8bd5\u7684\u6307\u6807\u7ed3\u679c\u6807\u7b7e\u3002 */
    private static final String OUTCOME_RETRY = "retry";
    /** \u8bb0\u5f55 Outbox \u505c\u6b62\u6295\u9012\u7684\u6307\u6807\u7ed3\u679c\u6807\u7b7e\u3002 */
    private static final String OUTCOME_DEAD = "dead";
    /** \u8bb0\u5f55 Inbox \u6d88\u8d39\u6210\u529f\u7684\u6307\u6807\u7ed3\u679c\u6807\u7b7e\u3002 */
    private static final String OUTCOME_PROCESSED = "processed";
    /** \u8bb0\u5f55 Inbox \u5ffd\u7565\u91cd\u590d\u6d88\u8d39\u7684\u6307\u6807\u7ed3\u679c\u6807\u7b7e\u3002 */
    private static final String OUTCOME_DUPLICATE = "duplicate";
    /** \u8bb0\u5f55\u6d88\u606f\u5904\u7406\u5931\u8d25\u7684\u6307\u6807\u7ed3\u679c\u6807\u7b7e\u3002 */
    private static final String OUTCOME_FAILED = "failed";

    /** \u7f13\u5b58\u5f53\u524d OpenTelemetry Meter \u7684 Outbox \u8ba1\u6570\u53e5\u67c4\u3002 */
    private static final AtomicReference<CounterHandle> OUTBOX_COUNTER = new AtomicReference<>();
    /** \u7f13\u5b58\u5f53\u524d OpenTelemetry Meter \u7684 Inbox \u8ba1\u6570\u53e5\u67c4\u3002 */
    private static final AtomicReference<CounterHandle> INBOX_COUNTER = new AtomicReference<>();

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MqDeliveryMetrics</code> \u5b9e\u4f8b\u3002 */
    private MqDeliveryMetrics() {
    }

    /**
     * 记录一条成功确认发布的 Outbox 消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void outboxPublished(String broker) {
        recordOutbox(broker, OUTCOME_PUBLISHED);
    }

    /**
     * 记录一条等待后续重试的 Outbox 消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void outboxRetry(String broker) {
        recordOutbox(broker, OUTCOME_RETRY);
    }

    /**
     * 记录一条已进入死信状态的 Outbox 消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void outboxDead(String broker) {
        recordOutbox(broker, OUTCOME_DEAD);
    }

    /**
     * 记录一条发送或确认失败、最终状态尚未确定的 Outbox 消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void outboxFailed(String broker) {
        recordOutbox(broker, OUTCOME_FAILED);
    }

    /**
     * 记录一条成功处理的 Inbox 消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void inboxProcessed(String broker) {
        recordInbox(broker, OUTCOME_PROCESSED);
    }

    /**
     * 记录一条被持久 Inbox 去重的重复消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void inboxDuplicate(String broker) {
        recordInbox(broker, OUTCOME_DUPLICATE);
    }

    /**
     * 记录一条业务处理失败、应由 broker 重投的 Inbox 消息。
     *
     * @param broker 消息中间件连接地址
     */
    public static void inboxFailed(String broker) {
        recordInbox(broker, OUTCOME_FAILED);
    }

    /**
     * \u5c06\u5e26\u4ee3\u7406\u548c\u5904\u7406\u7ed3\u679c\u6807\u7b7e\u7684 Outbox \u8ba1\u6570\u52a0\u4e00\u3002
     * @param broker \u5f53\u524d\u6295\u9012\u5bf9\u5e94\u7684\u6d88\u606f\u4ee3\u7406
     * @param outcome \u672c\u6b21\u6295\u9012\u6216\u6d88\u8d39\u7684\u7ed3\u679c\u6807\u7b7e
     */
    private static void recordOutbox(String broker, String outcome) {
        counter(OUTBOX_COUNTER, OUTBOX_DELIVERY_METRIC, "Count of ddd4j outbox delivery outcomes")
                .add(1, attributes(broker, outcome));
    }

    /**
     * \u5c06\u5e26\u4ee3\u7406\u548c\u5904\u7406\u7ed3\u679c\u6807\u7b7e\u7684 Inbox \u8ba1\u6570\u52a0\u4e00\u3002
     * @param broker \u5f53\u524d\u6295\u9012\u5bf9\u5e94\u7684\u6d88\u606f\u4ee3\u7406
     * @param outcome \u672c\u6b21\u6295\u9012\u6216\u6d88\u8d39\u7684\u7ed3\u679c\u6807\u7b7e
     */
    private static void recordInbox(String broker, String outcome) {
        counter(INBOX_COUNTER, INBOX_DELIVERY_METRIC, "Count of ddd4j inbox delivery outcomes")
                .add(1, attributes(broker, outcome));
    }

    /**
     * \u53d6\u5f97\u5f53\u524d Meter \u7684\u8ba1\u6570\u5668\u53e5\u67c4\uff0cMeter \u66f4\u65b0\u65f6\u521b\u5efa\u65b0\u8ba1\u6570\u5668\u3002
     * @param cache \u7f13\u5b58\u4e0e\u5f53\u524d Meter \u5bf9\u5e94\u7684\u8ba1\u6570\u5668\u53e5\u67c4
     * @param name \u5f53\u524d\u7ec4\u4ef6\u3001\u6307\u6807\u3001\u4efb\u52a1\u6216 Bean \u4f7f\u7528\u7684\u540d\u79f0
     * @param description \u7528\u4e8e\u6307\u6807\u91c7\u96c6\u7aef\u7406\u89e3\u8ba1\u6570\u8bed\u4e49\u7684\u8bf4\u660e
     * @return \u5bf9\u5e94\u5f53\u524d Meter \u7684\u6d88\u606f\u8ba1\u6570\u5668
     */
    private static LongCounter counter(AtomicReference<CounterHandle> cache, String name, String description) {
        Meter meter = Ddd4jOtel.meter();
        CounterHandle cached = cache.get();
        if (Objects.nonNull(cached) && cached.meter == meter) {
            return cached.counter;
        }
        CounterHandle created = new CounterHandle(
                meter,
                meter.counterBuilder(name)
                        .setUnit("{message}")
                        .setDescription(description)
                        .build()
        );
        cache.set(created);
        return created.counter;
    }

    /**
     * \u7ec4\u88c5\u8ba1\u6570\u4e8b\u4ef6\u4f7f\u7528\u7684\u4ee3\u7406\u548c\u7ed3\u679c\u6807\u7b7e\u3002
     * @param broker \u5f53\u524d\u6295\u9012\u5bf9\u5e94\u7684\u6d88\u606f\u4ee3\u7406
     * @param outcome \u672c\u6b21\u6295\u9012\u6216\u6d88\u8d39\u7684\u7ed3\u679c\u6807\u7b7e
     * @return OpenTelemetry \u6d88\u606f\u6295\u9012\u5c5e\u6027
     */
    private static Attributes attributes(String broker, String outcome) {
        return Attributes.of(
                Ddd4jOtel.ATTR_MESSAGING_SYSTEM,
                Objects.isNull(broker) ? "unknown" : broker,
                ATTR_DELIVERY_OUTCOME,
                outcome
        );
    }

    /** \u4fdd\u5b58\u6295\u9012\u8ba1\u6570\u5668\u53ca\u521b\u5efa\u5b83\u7684 Meter\uff0c\u4ee5\u4fbf\u6309 Meter \u590d\u7528\u6307\u6807\u3002 */
    private static final class CounterHandle {
        /** \u521b\u5efa\u8ba1\u6570\u5668\u65f6\u4f7f\u7528\u4e14\u5e94\u4e0e\u7f13\u5b58\u5339\u914d\u7684 Meter \u5b9e\u4f8b\u3002 */
        private final Meter meter;
        /** \u5f53\u524d Meter \u6ce8\u518c\u7684\u6d88\u606f\u6295\u9012\u8ba1\u6570\u5668\u3002 */
        private final LongCounter counter;

        /**
         * \u5c06\u8ba1\u6570\u5668\u7ed1\u5b9a\u5230\u521b\u5efa\u5b83\u7684 Meter\u3002
         * @param meter \u7528\u4e8e\u6ce8\u518c\u6307\u6807\u7684 OpenTelemetry Meter
         * @param counter \u5df2\u6ce8\u518c\u7684\u8ba1\u6570\u5668
         */
        CounterHandle(Meter meter, LongCounter counter) {
            this.meter = meter;
            this.counter = counter;
        }
    }
}
