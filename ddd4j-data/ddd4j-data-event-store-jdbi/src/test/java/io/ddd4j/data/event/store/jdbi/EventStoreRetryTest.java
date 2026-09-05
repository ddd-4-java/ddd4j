package io.ddd4j.data.event.store.jdbi;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventStoreRetryTest {

    /** 桩 Sleeper：仅记录调用次数与延迟，不真正睡眠。 */
    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        final List<Long> calls = new ArrayList<Long>();

        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }
    }

    @Test
    void firstAttemptSuccess_doesNotSleep() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        String result = retry.execute("op", () -> "ok");

        assertEquals("ok", result);
        assertTrue(sleeper.calls.isEmpty());
    }

    @Test
    void retriableFailureThenSuccess_sleepsOnce() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        final AtomicInteger attempts = new AtomicInteger(0);

        String result = retry.execute("op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new SQLIntegrityConstraintViolationException("uk_position violation");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, sleeper.calls.size());
    }

    @Test
    void retriableExhausted_throwsLastException() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        assertThrows(SQLIntegrityConstraintViolationException.class, () -> retry.execute("op", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        }));

        // 3 attempts => 2 sleeps between attempts
        assertEquals(2, sleeper.calls.size());
    }

    @Test
    void optimisticLockFailure_noRetry() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        final AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(IllegalStateException.class, () -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Version conflict");
        }));

        // 不可重试：只尝试一次，不睡眠
        assertEquals(1, attempts.get());
        assertTrue(sleeper.calls.isEmpty());
    }

    @Test
    void nonRetriableException_thrownImmediately() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        final AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(SQLException.class, () -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new SQLException("connection closed");
        }));

        assertEquals(1, attempts.get());
        assertTrue(sleeper.calls.isEmpty());
    }

    @Test
    void computeDelay_isExponentialWithJitter() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            long delay = EventStoreRetry.computeDelay(attempt);
            long expDelay = 10L << (attempt - 1);
            // jitter ∈ [0, 10]
            assertTrue(delay >= expDelay && delay <= expDelay + 10,
                    "attempt " + attempt + " delay " + delay + " not in [" + expDelay + "," + (expDelay + 10) + "]");
        }
    }

    @Test
    void invalidConstructorArgs_throwIAE() {
        RecordingSleeper sleeper = new RecordingSleeper();
        assertThrows(IllegalArgumentException.class, () -> new EventStoreRetry(0, 1L, sleeper));
        assertThrows(IllegalArgumentException.class, () -> new EventStoreRetry(3, -1L, sleeper));
    }
}
