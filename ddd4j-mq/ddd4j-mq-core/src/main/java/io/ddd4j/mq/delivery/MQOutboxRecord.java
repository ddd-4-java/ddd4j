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
 * 可持久化的 Outbox 消息快照。
 *
 * <p>该对象不绑定 JSON、数据库或 broker；存储适配器负责将其映射为自己的表结构。
 */

    public MQOutboxRecord(String messageId, String destination, String payload,
                          Map<String, String> headers, MQOutboxStatus status, Instant availableAt,
                          String leaseOwner, Instant leaseUntil, int attempts, String lastError,
                          Instant publishedAt) {
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

    public String messageId() { return messageId; }
    public String destination() { return destination; }
    public String payload() { return payload; }
    public Map<String, String> headers() { return headers; }
    public MQOutboxStatus status() { return status; }
    public Instant availableAt() { return availableAt; }
    public String leaseOwner() { return leaseOwner; }
    public Instant leaseUntil() { return leaseUntil; }
    public int attempts() { return attempts; }
    public String lastError() { return lastError; }
    public Instant publishedAt() { return publishedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MQOutboxRecord)) return false;
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

    @Override
    public String toString() {
        return "MQOutboxRecord{messageId=" + messageId + ", destination=" + destination
                + ", status=" + status + ", attempts=" + attempts + '}';
    }

    /**
     * 创建一条等待发布的消息。
     *
     * @param messageId 稳定消息标识
     * @param destination broker 目的地
     * @param payload 已序列化事件负载
     * @param headers 业务消息头
     * @param availableAt 首次可投递时间
     * @return 待发布记录
     */
    public static MQOutboxRecord pending(String messageId, String destination, String payload,
                                         Map<String, String> headers, Instant availableAt) {
        return new MQOutboxRecord(messageId, destination, payload, headers, MQOutboxStatus.PENDING,
                availableAt, null, null, 0, null, null);
    }

    public String getMessageId() {
        return messageId;
    }

    public String getDestination() {
        return destination;
    }

    public String getPayload() {
        return payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public MQOutboxStatus getStatus() {
        return status;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
