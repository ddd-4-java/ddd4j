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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 可持久化的 Outbox 消息快照。
 *
 * <p>该对象不绑定 JSON、数据库或 broker；存储适配器负责将其映射为自己的表结构。
 */
public final class MQOutboxRecord {

    private static final long serialVersionUID = 0L;

    private final String messageId;

    private final String destination;

    private final String payload;

    private final Map<String, String> headers;

    private final MQOutboxStatus status;

    private final Instant availableAt;

    private final String leaseOwner;

    private final Instant leaseUntil;

    private final int attempts;

    private final String lastError;

    private final Instant publishedAt;

    /**
     * 创建包含投递状态的不可变 Outbox 消息快照。
     *
     * @param messageId 消息标识
     * @param destination 消息目的地
     * @param payload 消息载荷
     * @param headers 消息头
     * @param status 投递状态
     * @param availableAt 下次可投递时间
     * @param leaseOwner 租约持有者
     * @param leaseUntil 租约截止时间
     * @param attempts 投递尝试次数
     * @param lastError 最后一次错误
     * @param publishedAt 确认发布时间
     */
    @JsonCreator()
    public MQOutboxRecord(@JsonProperty("messageId") String messageId, @JsonProperty("destination") String destination, @JsonProperty("payload") String payload, @JsonProperty("headers") Map<String, String> headers, @JsonProperty("status") MQOutboxStatus status, @JsonProperty("availableAt") Instant availableAt, @JsonProperty("leaseOwner") String leaseOwner, @JsonProperty("leaseUntil") Instant leaseUntil, @JsonProperty("attempts") int attempts, @JsonProperty("lastError") String lastError, @JsonProperty("publishedAt") Instant publishedAt) {
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
        Map<String, String> mutableHeaders = new LinkedHashMap<>(headers);
        mutableHeaders.put(MQDeliveryHeaders.MESSAGE_ID, messageId);
        headers = Map.copyOf(mutableHeaders);
        this.messageId = messageId;
        this.destination = destination;
        this.payload = payload;
        this.headers = headers;
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
    public static MQOutboxRecord pending(String messageId, String destination, String payload, Map<String, String> headers, Instant availableAt) {
        return new MQOutboxRecord(messageId, destination, payload, headers, MQOutboxStatus.PENDING, availableAt, null, null, 0, null, null);
    }

    /**
     * 返回投递尝试次数。
     *
     * @return 投递尝试次数
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * 返回下次可投递时间。
     *
     * @return 下次可投递时间
     */
    public Instant getAvailableAt() {
        return availableAt;
    }

    /**
     * 返回消息目的地。
     *
     * @return 消息目的地
     */
    public String getDestination() {
        return destination;
    }

    /**
     * 返回消息头。
     *
     * @return 消息头
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * 返回最后一次错误。
     *
     * @return 最后一次错误
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 返回租约持有者。
     *
     * @return 租约持有者
     */
    public String getLeaseOwner() {
        return leaseOwner;
    }

    /**
     * 返回租约截止时间。
     *
     * @return 租约截止时间
     */
    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    /**
     * 返回消息标识。
     *
     * @return 消息标识
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 返回消息载荷。
     *
     * @return 消息载荷
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 返回确认发布时间。
     *
     * @return 确认发布时间
     */
    public Instant getPublishedAt() {
        return publishedAt;
    }

    /**
     * 返回投递状态。
     *
     * @return 投递状态
     */
    public MQOutboxStatus getStatus() {
        return status;
    }

    /**
     * 返回消息标识。
     *
     * @return 消息标识
     */
    @JsonProperty("messageId")
    public String messageId() {
        return messageId;
    }

    /**
     * 返回消息目的地。
     *
     * @return 消息目的地
     */
    @JsonProperty("destination")
    public String destination() {
        return destination;
    }

    /**
     * 返回消息载荷。
     *
     * @return 消息载荷
     */
    @JsonProperty("payload")
    public String payload() {
        return payload;
    }

    /**
     * 返回消息头。
     *
     * @return 消息头
     */
    @JsonProperty("headers")
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * 返回投递状态。
     *
     * @return 投递状态
     */
    @JsonProperty("status")
    public MQOutboxStatus status() {
        return status;
    }

    /**
     * 返回下次可投递时间。
     *
     * @return 下次可投递时间
     */
    @JsonProperty("availableAt")
    public Instant availableAt() {
        return availableAt;
    }

    /**
     * 返回租约持有者。
     *
     * @return 租约持有者
     */
    @JsonProperty("leaseOwner")
    public String leaseOwner() {
        return leaseOwner;
    }

    /**
     * 返回租约截止时间。
     *
     * @return 租约截止时间
     */
    @JsonProperty("leaseUntil")
    public Instant leaseUntil() {
        return leaseUntil;
    }

    /**
     * 返回投递尝试次数。
     *
     * @return 投递尝试次数
     */
    @JsonProperty("attempts")
    public int attempts() {
        return attempts;
    }

    /**
     * 返回最后一次错误。
     *
     * @return 最后一次错误
     */
    @JsonProperty("lastError")
    public String lastError() {
        return lastError;
    }

    /**
     * 返回确认发布时间。
     *
     * @return 确认发布时间
     */
    @JsonProperty("publishedAt")
    public Instant publishedAt() {
        return publishedAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        MQOutboxRecord other = (MQOutboxRecord) obj;
        return Objects.equals(this.messageId, other.messageId) && Objects.equals(this.destination, other.destination) && Objects.equals(this.payload, other.payload) && Objects.equals(this.headers, other.headers) && Objects.equals(this.status, other.status) && Objects.equals(this.availableAt, other.availableAt) && Objects.equals(this.leaseOwner, other.leaseOwner) && Objects.equals(this.leaseUntil, other.leaseUntil) && this.attempts == other.attempts && Objects.equals(this.lastError, other.lastError) && Objects.equals(this.publishedAt, other.publishedAt);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(messageId);
        result = 31 * result + Objects.hashCode(destination);
        result = 31 * result + Objects.hashCode(payload);
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(availableAt);
        result = 31 * result + Objects.hashCode(leaseOwner);
        result = 31 * result + Objects.hashCode(leaseUntil);
        result = 31 * result + Integer.hashCode(attempts);
        result = 31 * result + Objects.hashCode(lastError);
        result = 31 * result + Objects.hashCode(publishedAt);
        return result;
    }

    @Override
    public String toString() {
        return "MQOutboxRecord[messageId=" + messageId + ", destination=" + destination + ", payload=" + payload + ", headers=" + headers + ", status=" + status + ", availableAt=" + availableAt + ", leaseOwner=" + leaseOwner + ", leaseUntil=" + leaseUntil + ", attempts=" + attempts + ", lastError=" + lastError + ", publishedAt=" + publishedAt + "]";
    }
}
