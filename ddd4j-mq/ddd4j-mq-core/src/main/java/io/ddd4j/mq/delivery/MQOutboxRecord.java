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

package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 可持久化的 Outbox 消息快照。
 *
 * <p>该对象不绑定 JSON、数据库或 broker；存储适配器负责将其映射为自己的表结构。
 */
public final class MQOutboxRecord {

    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private final String messageId;
    /** \u8bb0\u5f55\u53d1\u5e03\u65f6\u4f7f\u7528\u7684\u76ee\u6807\u4e3b\u9898\u6216\u5730\u5740\u3002 */
    private final String destination;
    /** \u4e8b\u4ef6\u5199\u5165 Outbox \u65f6\u5e8f\u5217\u5316\u7684\u6d88\u606f\u8d1f\u8f7d\u3002 */
    private final String payload;
    /** \u6295\u9012\u8bb0\u5f55\u643a\u5e26\u7684\u6d88\u606f\u5143\u6570\u636e\u3002 */
    private final Map<String, String> headers;
    /** \u5f53\u524d Outbox \u8bb0\u5f55\u7684\u6295\u9012\u72b6\u6001\u3002 */
    private final MQOutboxStatus status;
    /** \u8bb0\u5f55\u4e0b\u6b21\u53ef\u88ab\u8ba4\u9886\u6295\u9012\u7684\u65f6\u95f4\u3002 */
    private final Instant availableAt;
    /** \u5f53\u524d\u5bf9\u8be5\u8bb0\u5f55\u6301\u6709\u6295\u9012\u79df\u7ea6\u7684\u6d3e\u53d1\u5668\u6807\u8bc6\u3002 */
    private final String leaseOwner;
    /** \u5f53\u524d\u72ec\u5360\u6295\u9012\u79df\u7ea6\u7684\u5931\u6548\u65f6\u95f4\u3002 */
    private final Instant leaseUntil;
    /** \u5f53\u524d Outbox \u8bb0\u5f55\u7d2f\u8ba1\u5c1d\u8bd5\u7684\u53d1\u9001\u6b21\u6570\u3002 */
    private final int attempts;
    /** \u4e0a\u6b21\u6295\u9012\u5931\u8d25\u7684\u8bca\u65ad\u8bf4\u660e\u3002 */
    private final String lastError;
    /** \u6b64\u8bb0\u5f55\u6210\u529f\u6807\u8bb0\u4e3a\u53d1\u5e03\u5b8c\u6210\u7684\u65f6\u95f4\u3002 */
    private final Instant publishedAt;

    /**
     * 可持久化的 Outbox 消息快照。
     *
     * <p>该对象不绑定 JSON、数据库或 broker；存储适配器负责将其映射为自己的表结构。
 * @param messageId 消息标识
 * @param destination 目标对象
 * @param payload 消息负载
 * @param headers 消息头集合
 * @param status 状态
 * @param availableAt 参数 availableAt
 * @param leaseOwner 字符串参数
 * @param leaseUntil 参数 leaseUntil
 * @param attempts 整型数值
 * @param lastError 末个错误
 * @param publishedAt 参数 publishedAt
     */

    @JsonCreator
    public MQOutboxRecord(@JsonProperty("messageId") String messageId,
                          @JsonProperty("destination") String destination,
                          @JsonProperty("payload") String payload,
                          @JsonProperty("headers") Map<String, String> headers,
                          @JsonProperty("status") MQOutboxStatus status,
                          @JsonProperty("availableAt") Instant availableAt,
                          @JsonProperty("leaseOwner") String leaseOwner,
                          @JsonProperty("leaseUntil") Instant leaseUntil,
                          @JsonProperty("attempts") int attempts,
                          @JsonProperty("lastError") String lastError,
                          @JsonProperty("publishedAt") Instant publishedAt) {
        if (StrKit.isBlank(messageId)) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (StrKit.isBlank(destination)) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(availableAt, "availableAt must not be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        Map<String, String> mutableHeaders = new LinkedHashMap<String, String>(headers);
        mutableHeaders.put(MQDeliveryHeaders.MESSAGE_ID, messageId);
        for (Map.Entry<String, String> header : mutableHeaders.entrySet()) {
            Objects.requireNonNull(header.getKey(), "header key must not be null");
            Objects.requireNonNull(header.getValue(), "header value must not be null");
        }
        this.messageId = messageId;
        this.destination = destination;
        this.payload = payload;
        this.headers = Collections.unmodifiableMap(mutableHeaders);
        this.status = status;
        this.availableAt = availableAt;
        this.leaseOwner = leaseOwner;
        this.leaseUntil = leaseUntil;
        this.attempts = attempts;
        this.lastError = lastError;
        this.publishedAt = publishedAt;
    }

    /**
     * 创建一条等待发布的消息。
     *
     * @param messageId   稳定消息标识
     * @param destination broker 目的地
     * @param payload     已序列化事件负载
     * @param headers     业务消息头
     * @param availableAt 首次可投递时间
     * @return 待发布记录
     */
    public static MQOutboxRecord pending(String messageId, String destination, String payload,
                                         Map<String, String> headers, Instant availableAt) {
        return new MQOutboxRecord(messageId, destination, payload, headers, MQOutboxStatus.PENDING,
                availableAt, null, null, 0, null, null);
    }

    /** @return 稳定消息标识 */
    public String messageId() {
        return messageId;
    }

    /** @return Broker 目的地 */
    public String destination() {
        return destination;
    }

    /** @return 已序列化事件负载 */
    public String payload() {
        return payload;
    }

    /** @return 不可修改的消息头快照 */
    public Map<String, String> headers() {
        return headers;
    }

    /** @return 当前投递状态 */
    public MQOutboxStatus status() {
        return status;
    }

    /** @return 下一次可投递时间 */
    public Instant availableAt() {
        return availableAt;
    }

    /** @return 当前租约持有者 */
    public String leaseOwner() {
        return leaseOwner;
    }

    /** @return 当前租约截止时间 */
    public Instant leaseUntil() {
        return leaseUntil;
    }

    /** @return 已尝试投递次数 */
    public int attempts() {
        return attempts;
    }

    /** @return 最近一次投递错误 */
    public String lastError() {
        return lastError;
    }

    /** @return 确认发布的时间 */
    public Instant publishedAt() {
        return publishedAt;
    }

    /**
     * 根据<code>leaseOwner</code>、<code>leaseUntil</code>、<code>lastError</code>、<code>publishedAt</code>、<code>attempts</code>、<code>status</code>比较对象的值是否相等。
     *
     * @param o 用来比较的另一个对象，允许为空
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MQOutboxRecord)) {
            return false;
        }
        MQOutboxRecord that = (MQOutboxRecord) o;
        return attempts == that.attempts
                && messageId.equals(that.messageId)
                && destination.equals(that.destination)
                && payload.equals(that.payload)
                && headers.equals(that.headers)
                && status == that.status
                && availableAt.equals(that.availableAt)
                && Objects.equals(leaseOwner, that.leaseOwner)
                && Objects.equals(leaseUntil, that.leaseUntil)
                && Objects.equals(lastError, that.lastError)
                && Objects.equals(publishedAt, that.publishedAt);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = messageId.hashCode();
        result = 31 * result + destination.hashCode();
        result = 31 * result + payload.hashCode();
        result = 31 * result + headers.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + availableAt.hashCode();
        result = 31 * result + Objects.hashCode(leaseOwner);
        result = 31 * result + Objects.hashCode(leaseUntil);
        result = 31 * result + attempts;
        result = 31 * result + Objects.hashCode(lastError);
        result = 31 * result + Objects.hashCode(publishedAt);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>messageId</code>、<code>destination</code>、<code>payload</code>、<code>headers</code>、<code>status</code>、<code>availableAt</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "MQOutboxRecord[messageId=" + messageId + ", destination=" + destination
                + ", payload=" + payload + ", headers=" + headers + ", status=" + status
                + ", availableAt=" + availableAt + ", leaseOwner=" + leaseOwner
                + ", leaseUntil=" + leaseUntil + ", attempts=" + attempts
                + ", lastError=" + lastError + ", publishedAt=" + publishedAt + ']';
    }

    /** @return 稳定消息标识 */
    public String getMessageId() {
        return messageId;
    }

    /** @return Broker 目的地 */
    public String getDestination() {
        return destination;
    }

    /** @return 已序列化事件负载 */
    public String getPayload() {
        return payload;
    }

    /** @return 不可修改的消息头快照 */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** @return 当前投递状态 */
    public MQOutboxStatus getStatus() {
        return status;
    }

    /** @return 下一次可投递时间 */
    public Instant getAvailableAt() {
        return availableAt;
    }

    /** @return 当前租约持有者 */
    public String getLeaseOwner() {
        return leaseOwner;
    }

    /** @return 当前租约截止时间 */
    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    /** @return 已尝试投递次数 */
    public int getAttempts() {
        return attempts;
    }

    /** @return 最近一次投递错误 */
    public String getLastError() {
        return lastError;
    }

    /** @return 确认发布的时间 */
    public Instant getPublishedAt() {
        return publishedAt;
    }
}
