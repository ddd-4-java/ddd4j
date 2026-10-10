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

import java.util.Objects;

/**
 * 单次 Outbox 调度的汇总结果。
 */

public final class MQOutboxDispatchResult {

    private static final long serialVersionUID = 0L;

    private final int claimed;

    private final int published;

    private final int rescheduled;

    private final int dead;

    private final int confirmationLost;

    /**
 * @param claimed 已领取数量
 * @param published 已确认发布数量
 * @param rescheduled 已安排重试数量
 * @param dead 预计进入死信数量
 * @param confirmationLost broker 已接收但租约确认丢失数量，后续可能重复投递
 */

    @JsonCreator()
    public MQOutboxDispatchResult(@JsonProperty("claimed") int claimed, @JsonProperty("published") int published, @JsonProperty("rescheduled") int rescheduled, @JsonProperty("dead") int dead, @JsonProperty("confirmationLost") int confirmationLost) {
        this.claimed = claimed;
        this.published = published;
        this.rescheduled = rescheduled;
        this.dead = dead;
        this.confirmationLost = confirmationLost;
    }

    @JsonProperty("claimed")
    public int claimed() {
        return claimed;
    }

    @JsonProperty("published")
    public int published() {
        return published;
    }

    @JsonProperty("rescheduled")
    public int rescheduled() {
        return rescheduled;
    }

    @JsonProperty("dead")
    public int dead() {
        return dead;
    }

    @JsonProperty("confirmationLost")
    public int confirmationLost() {
        return confirmationLost;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        MQOutboxDispatchResult other = (MQOutboxDispatchResult) obj;
        return this.claimed == other.claimed && this.published == other.published && this.rescheduled == other.rescheduled && this.dead == other.dead && this.confirmationLost == other.confirmationLost;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(claimed);
        result = 31 * result + Integer.hashCode(published);
        result = 31 * result + Integer.hashCode(rescheduled);
        result = 31 * result + Integer.hashCode(dead);
        result = 31 * result + Integer.hashCode(confirmationLost);
        return result;
    }

    @Override
    public String toString() {
        return "MQOutboxDispatchResult[claimed=" + claimed + ", published=" + published + ", rescheduled=" + rescheduled + ", dead=" + dead + ", confirmationLost=" + confirmationLost + "]";
    }
}
