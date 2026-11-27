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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证投递租约、退避和重试耗尽的公开契约。
 */
class MQDeliveryPolicyTest {

    /**
     * 验证<code>policy.leaseDuration()</code> 等于 <code>Duration.ofSeconds(60)</code>，<code>policy.maxAttempts()</code> 等于 <code>12</code>，<code>policy.jitterFactor()</code> 等于 <code>0.20D</code>。
     */
    @Test
    void productionDefault_shouldExposeTheDocumentedReliabilityDefaults() {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();

        assertEquals(Duration.ofSeconds(60), policy.leaseDuration());
        assertEquals(12, policy.maxAttempts());
        assertEquals(Duration.ofSeconds(1), policy.initialBackoff());
        assertEquals(Duration.ofMinutes(5), policy.maxBackoff());
        assertEquals(0.20D, policy.jitterFactor());
    }

    /**
     * 验证<code>policy.nextAvailableAt(1, failedAt, 0.50D)</code> 等于 <code>failedAt.plusSeconds(1)</code>，<code>policy.nextAvailableAt(3, failedAt, 0.50D)</code> 等于 <code>failedAt.plusSeconds(4)</code>，<code>policy.nextAvailableAt(1, failedAt, 1.0D)</code> 等于 <code>failedAt.plusMillis(1200)</code>。
     */
    @Test
    void nextAvailableAt_shouldApplyCappedExponentialBackoffAndJitter() {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        Instant failedAt = Instant.parse("2026-08-03T00:00:00Z");

        assertEquals(failedAt.plusSeconds(1), policy.nextAvailableAt(1, failedAt, 0.50D));
        assertEquals(failedAt.plusSeconds(4), policy.nextAvailableAt(3, failedAt, 0.50D));
        assertEquals(failedAt.plusSeconds(300), policy.nextAvailableAt(20, failedAt, 0.50D));
        assertEquals(failedAt.plusMillis(800), policy.nextAvailableAt(1, failedAt, 0.0D));
        assertEquals(failedAt.plusMillis(1200), policy.nextAvailableAt(1, failedAt, 1.0D));
    }

    /**
     * 验证<code>policy.exhausted(11)</code>为假，<code>policy.exhausted(12)</code>为真。
     */
    @Test
    void exhausted_shouldStartAtConfiguredMaximum() {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();

        assertFalse(policy.exhausted(11));
        assertTrue(policy.exhausted(12));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>policy.nextAvailableAt( 1, Instant.parse("2026-08-03T00:00:00Z"), Double.NaN)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void nonFiniteJitter_shouldBeRejectedBeforeScheduling() {
        assertThrows(IllegalArgumentException.class, () -> new MQDeliveryPolicy(
                Duration.ofSeconds(60), 3, Duration.ofSeconds(1), Duration.ofSeconds(5), Double.NaN));
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        assertThrows(IllegalArgumentException.class, () -> policy.nextAvailableAt(
                1, Instant.parse("2026-08-03T00:00:00Z"), Double.NaN));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void constructor_shouldRejectInvalidDeliverySettings() {
        assertThrows(IllegalArgumentException.class, () -> new MQDeliveryPolicy(
                Duration.ZERO, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new MQDeliveryPolicy(
                Duration.ofSeconds(1), 0, Duration.ofSeconds(1), Duration.ofSeconds(1), 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new MQDeliveryPolicy(
                Duration.ofSeconds(1), 1, Duration.ofSeconds(2), Duration.ofSeconds(1), 0.0D));
    }
}
