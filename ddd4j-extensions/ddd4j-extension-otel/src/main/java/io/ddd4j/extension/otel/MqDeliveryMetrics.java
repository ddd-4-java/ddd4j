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

    /**
     * 记录 Outbox 成功发布的指标结果标签。
     */
    private static final String OUTCOME_PUBLISHED = "published";
    /**
     * 记录 Outbox 重新安排重试的指标结果标签。
     */
    private static final String OUTCOME_RETRY = "retry";
    /**
     * 记录 Outbox 停止投递的指标结果标签。
     */
    private static final String OUTCOME_DEAD = "dead";
    /**
     * 记录 Inbox 消费成功的指标结果标签。
     */
    private static final String OUTCOME_PROCESSED = "processed";
    /**
     * 记录 Inbox 忽略重复消费的指标结果标签。
     */
    private static final String OUTCOME_DUPLICATE = "duplicate";
    /**
     * 记录消息处理失败的指标结果标签。
     */
    private static final String OUTCOME_FAILED = "failed";

    /**
     * 缓存当前 OpenTelemetry Meter 的 Outbox 计数句柄。
     */
    private static final AtomicReference<CounterHandle> OUTBOX_COUNTER = new AtomicReference<>();
    /**
     * 缓存当前 OpenTelemetry Meter 的 Inbox 计数句柄。
     */
    private static final AtomicReference<CounterHandle> INBOX_COUNTER = new AtomicReference<>();

    /**
     * 限制调用方直接创建 <code>MqDeliveryMetrics</code> 实例。
     */
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
     * 将带代理和处理结果标签的 Outbox 计数加一。
     *
     * @param broker  当前投递对应的消息代理
     * @param outcome 本次投递或消费的结果标签
     */
    private static void recordOutbox(String broker, String outcome) {
        counter(OUTBOX_COUNTER, OUTBOX_DELIVERY_METRIC, "Count of ddd4j outbox delivery outcomes")
                .add(1, attributes(broker, outcome));
    }

    /**
     * 将带代理和处理结果标签的 Inbox 计数加一。
     *
     * @param broker  当前投递对应的消息代理
     * @param outcome 本次投递或消费的结果标签
     */
    private static void recordInbox(String broker, String outcome) {
        counter(INBOX_COUNTER, INBOX_DELIVERY_METRIC, "Count of ddd4j inbox delivery outcomes")
                .add(1, attributes(broker, outcome));
    }

    /**
     * 取得当前 Meter 的计数器句柄，Meter 更新时创建新计数器。
     *
     * @param cache       缓存与当前 Meter 对应的计数器句柄
     * @param name        当前组件、指标、任务或 Bean 使用的名称
     * @param description 用于指标采集端理解计数语义的说明
     * @return 对应当前 Meter 的消息计数器
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
     * 组装计数事件使用的代理和结果标签。
     *
     * @param broker  当前投递对应的消息代理
     * @param outcome 本次投递或消费的结果标签
     * @return OpenTelemetry 消息投递属性
     */
    private static Attributes attributes(String broker, String outcome) {
        return Attributes.of(
                Ddd4jOtel.ATTR_MESSAGING_SYSTEM,
                Objects.isNull(broker) ? "unknown" : broker,
                ATTR_DELIVERY_OUTCOME,
                outcome
        );
    }

    /**
     * 保存投递计数器及创建它的 Meter，以便按 Meter 复用指标。
     */
    private static final class CounterHandle {
        /**
         * 创建计数器时使用且应与缓存匹配的 Meter 实例。
         */
        private final Meter meter;
        /**
         * 当前 Meter 注册的消息投递计数器。
         */
        private final LongCounter counter;

        /**
         * 将计数器绑定到创建它的 Meter。
         *
         * @param meter   用于注册指标的 OpenTelemetry Meter
         * @param counter 已注册的计数器
         */
        CounterHandle(Meter meter, LongCounter counter) {
            this.meter = meter;
            this.counter = counter;
        }
    }
}
