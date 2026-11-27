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

    /**
     * 测试结束后执行 <code>Thread.interrupted()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position"))</code>为真，<code>EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected"))</code>为真。
     */
    @Test
    void sqlConstraintKeywordsAreRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position")));
        assertTrue(EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected")));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new SQLException("connection lost"))</code>为假，<code>EventStoreRetry.isRetriable(new RuntimeException("other"))</code>为假，<code>EventStoreRetry.isRetriable(new NullPointerException())</code>为假。
     */
    @Test
    void unrelatedFailuresAreNotRetriable() {
        assertFalse(EventStoreRetry.isRetriable(new SQLException("connection lost")));
        assertFalse(EventStoreRetry.isRetriable(new RuntimeException("other")));
        assertFalse(EventStoreRetry.isRetriable(new NullPointerException()));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapped)</code>为真。
     */
    @Test
    void nestedSqlFailureIsRetriable() {
        RuntimeException wrapped = new RuntimeException("wrapper", new SQLException("uk_position"));
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation"))</code>为真，<code>EventStoreRetry.isRetriable(new RuntimeException("Duplicate entry for key uk_position"))</code>为真，<code>EventStoreRetry.isRetriable(new RuntimeException("outer wrapper", deep))</code>为真。
     */
    @Test
    void wrappedRuntimeMessagesAreRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation")));
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Duplicate entry for key uk_position")));
        RuntimeException deep = new RuntimeException("uk_position violation");
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("outer wrapper", deep)));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new RuntimeException("persistence wrapper", cause))</code>为真。
     */
    @Test
    void constraintViolationClassNameIsRetriableWithoutKeyword() {
        Throwable cause = new ConstraintViolationException("schema constraint rejected");
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("persistence wrapper", cause)));
    }

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>failure.getMessage().contains("interrupted")</code>为真，<code>Thread.currentThread().isInterrupted()</code>为真。
     */
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

    /**
     * 用于触发被测运行时异常分支的测试异常。
     */
    private static final class ConstraintViolationException extends RuntimeException {
        /**
         * 使用 <code>super()</code> 初始化 <code>ConstraintViolationException</code> 测试夹具。
         *
         * @param message 测试传递或用于诊断的消息
         */
        private ConstraintViolationException(String message) {
            super(message);
        }
    }
}
