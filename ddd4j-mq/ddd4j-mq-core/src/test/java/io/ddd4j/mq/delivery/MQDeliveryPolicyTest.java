package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MQDeliveryPolicyTest {

    @Test
    void productionDefault_shouldExposeTheDocumentedReliabilityDefaults() {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();

        assertEquals(Duration.ofSeconds(60), policy.leaseDuration());
        assertEquals(12, policy.maxAttempts());
        assertEquals(Duration.ofSeconds(1), policy.initialBackoff());
        assertEquals(Duration.ofMinutes(5), policy.maxBackoff());
        assertEquals(0.20D, policy.jitterFactor());
    }

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

    @Test
    void exhausted_shouldStartAtConfiguredMaximum() {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();

        assertFalse(policy.exhausted(11));
        assertTrue(policy.exhausted(12));
    }

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
