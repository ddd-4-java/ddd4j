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

/**
 * 单次 Outbox 调度的汇总结果。
 *
 * @param claimed 已领取数量
 * @param published 已确认发布数量
 * @param rescheduled 已安排重试数量
 * @param dead 预计进入死信数量
 * @param confirmationLost broker 已接收但租约确认丢失数量，后续可能重复投递
 */
public final class MQOutboxDispatchResult {

    private final int claimed;
    private final int published;
    private final int rescheduled;
    private final int dead;
    private final int confirmationLost;

/**
 * 单次 Outbox 调度的汇总结果。
 *
 * @param claimed 已领取数量
 * @param published 已确认发布数量
 * @param rescheduled 已安排重试数量
 * @param dead 预计进入死信数量
 * @param confirmationLost broker 已接收但租约确认丢失数量，后续可能重复投递
 */

    public MQOutboxDispatchResult(int claimed, int published, int rescheduled, int dead,
                                  int confirmationLost) {
        this.claimed = claimed;
        this.published = published;
        this.rescheduled = rescheduled;
        this.dead = dead;
        this.confirmationLost = confirmationLost;
    }

    public int claimed() { return claimed; }
    public int published() { return published; }
    public int rescheduled() { return rescheduled; }
    public int dead() { return dead; }
    public int confirmationLost() { return confirmationLost; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MQOutboxDispatchResult)) return false;
        MQOutboxDispatchResult that = (MQOutboxDispatchResult) o;
        return claimed == that.claimed && published == that.published
                && rescheduled == that.rescheduled && dead == that.dead
                && confirmationLost == that.confirmationLost;
    }

    @Override
    public int hashCode() {
        int result = claimed;
        result = 31 * result + published;
        result = 31 * result + rescheduled;
        result = 31 * result + dead;
        result = 31 * result + confirmationLost;
        return result;
    }

    @Override
    public String toString() {
        return "MQOutboxDispatchResult{claimed=" + claimed + ", published=" + published
                + ", rescheduled=" + rescheduled + ", dead=" + dead
                + ", confirmationLost=" + confirmationLost + '}';
    }

    public int getClaimed() {
        return claimed;
    }

    public int getPublished() {
        return published;
    }

    public int getRescheduled() {
        return rescheduled;
    }

    public int getDead() {
        return dead;
    }

    public int getConfirmationLost() {
        return confirmationLost;
    }
}
