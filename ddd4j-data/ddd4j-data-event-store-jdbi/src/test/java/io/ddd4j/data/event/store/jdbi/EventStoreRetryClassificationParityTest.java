package io.ddd4j.data.event.store.jdbi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the retry classification and interrupt contract shared by all three lines. */
class EventStoreRetryClassificationParityTest {

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    void sqlConstraintKeywordsAreRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position")));
        assertTrue(EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected")));
    }

    @Test
    void unrelatedFailuresAreNotRetriable() {
        assertFalse(EventStoreRetry.isRetriable(new SQLException("connection lost")));
        assertFalse(EventStoreRetry.isRetriable(new RuntimeException("other")));
        assertFalse(EventStoreRetry.isRetriable(new NullPointerException()));
    }

    @Test
    void nestedSqlFailureIsRetriable() {
        RuntimeException wrapped = new RuntimeException("wrapper", new SQLException("uk_position"));
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    @Test
    void wrappedRuntimeMessagesAreRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation")));
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Duplicate entry for key uk_position")));
        RuntimeException deep = new RuntimeException("uk_position violation");
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("outer wrapper", deep)));
    }

    @Test
    void constraintViolationClassNameIsRetriableWithoutKeyword() {
        Throwable cause = new ConstraintViolationException("schema constraint rejected");
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("persistence wrapper", cause)));
    }

    @Test
    void interruptedRetryRestoresThreadFlag() {
        EventStoreRetry retry = new EventStoreRetry(3, 1L, millis -> {
            throw new InterruptedException("temporary interrupt");
        });
        RuntimeException failure = assertThrows(RuntimeException.class, () -> retry.execute("append", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        }));
        assertTrue(failure.getMessage().contains("interrupted"));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static final class ConstraintViolationException extends RuntimeException {
        private ConstraintViolationException(String message) {
            super(message);
        }
    }
}
