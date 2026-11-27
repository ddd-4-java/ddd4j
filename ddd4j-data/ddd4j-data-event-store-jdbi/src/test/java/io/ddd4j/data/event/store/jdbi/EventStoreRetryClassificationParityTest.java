package io.ddd4j.data.event.store.jdbi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the retry classification and interrupt contract shared by all three lines.
 */
class EventStoreRetryClassificationParityTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>Thread.interrupted()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position"))</code>\u4e3a\u771f\uff0c<code>EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected"))</code>\u4e3a\u771f\u3002 */
    @Test
    void sqlConstraintKeywordsAreRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position")));
        assertTrue(EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected")));
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(new SQLException("connection lost"))</code>\u4e3a\u5047\uff0c<code>EventStoreRetry.isRetriable(new RuntimeException("other"))</code>\u4e3a\u5047\uff0c<code>EventStoreRetry.isRetriable(new NullPointerException())</code>\u4e3a\u5047\u3002 */
    @Test
    void unrelatedFailuresAreNotRetriable() {
        assertFalse(EventStoreRetry.isRetriable(new SQLException("connection lost")));
        assertFalse(EventStoreRetry.isRetriable(new RuntimeException("other")));
        assertFalse(EventStoreRetry.isRetriable(new NullPointerException()));
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(wrapped)</code>\u4e3a\u771f\u3002 */
    @Test
    void nestedSqlFailureIsRetriable() {
        RuntimeException wrapped = new RuntimeException("wrapper", new SQLException("uk_position"));
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation"))</code>\u4e3a\u771f\uff0c<code>EventStoreRetry.isRetriable(new RuntimeException("Duplicate entry for key uk_position"))</code>\u4e3a\u771f\uff0c<code>EventStoreRetry.isRetriable(new RuntimeException("outer wrapper", deep))</code>\u4e3a\u771f\u3002 */
    @Test
    void wrappedRuntimeMessagesAreRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation")));
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Duplicate entry for key uk_position")));
        RuntimeException deep = new RuntimeException("uk_position violation");
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("outer wrapper", deep)));
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(new RuntimeException("persistence wrapper", cause))</code>\u4e3a\u771f\u3002 */
    @Test
    void constraintViolationClassNameIsRetriableWithoutKeyword() {
        Throwable cause = new ConstraintViolationException("schema constraint rejected");
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("persistence wrapper", cause)));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>failure.getMessage().contains("interrupted")</code>\u4e3a\u771f\uff0c<code>Thread.currentThread().isInterrupted()</code>\u4e3a\u771f\u3002 */
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

    /** \u7528\u4e8e\u89e6\u53d1\u88ab\u6d4b\u8fd0\u884c\u65f6\u5f02\u5e38\u5206\u652f\u7684\u6d4b\u8bd5\u5f02\u5e38\u3002 */
    private static final class ConstraintViolationException extends RuntimeException {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>ConstraintViolationException</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
         */
        private ConstraintViolationException(String message) {
            super(message);
        }
    }
}
