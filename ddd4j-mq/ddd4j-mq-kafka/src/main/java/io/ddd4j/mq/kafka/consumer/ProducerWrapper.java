package io.ddd4j.mq.kafka.consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 生产者包装类，包含生产者实例和最后使用时间
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class ProducerWrapper {

    private final KafkaProducer<String, String> producer;
    private final ReentrantLock lock = new ReentrantLock();
    @Getter
    private volatile long lastUsedTime;
    private volatile TransactionState transactionState = TransactionState.READY;

    public ProducerWrapper(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.lastUsedTime = System.currentTimeMillis();
    }

    public Future<RecordMetadata> send(ProducerRecord<String, String> record, Callback callback) {
        lock.lock();
        try {
            if (transactionState == TransactionState.FATAL_ERROR ||
                    transactionState == TransactionState.ABORTABLE_ERROR) {
                log.error("Cannot send message in error state: {}", transactionState);
                throw new IllegalStateException("Producer is in error state: " + transactionState);
            }
            this.lastUsedTime = System.currentTimeMillis();
            return producer.send(record, callback);
        } finally {
            lock.unlock();
        }
    }

    public synchronized void close(Duration timeout) {
        lock.lock();
        try {
            if (transactionState == TransactionState.IN_TRANSACTION) {
                try {
                    abortTransaction();
                } catch (Exception e) {
                    log.error("Failed to abort transaction during close", e);
                }
            }
            producer.close(timeout);
        } finally {
            lock.unlock();
        }
    }

    public KafkaProducer<String, String> getProducer() {
        this.lastUsedTime = System.currentTimeMillis();
        return producer;
    }

    public synchronized boolean beginTransaction() {
        lock.lock();
        try {
            if (transactionState == TransactionState.FATAL_ERROR ||
                    transactionState == TransactionState.ABORTABLE_ERROR) {
                log.error("Cannot begin transaction in error state: {}", transactionState);
                throw new IllegalStateException("Producer is in error state: " + transactionState);
            }

            if (transactionState != TransactionState.READY) {
                log.error("Invalid state transition from {} to {}", transactionState, TransactionState.IN_TRANSACTION);
                return false;
            }

            try {
                log.info("Begin transaction");
                producer.beginTransaction();
                transactionState = TransactionState.IN_TRANSACTION;
                log.info("Transaction into state: {}", transactionState);
                return true;
            } catch (Exception e) {
                log.error("Failed to begin transaction", e);
                transactionState = TransactionState.ABORTABLE_ERROR;
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    public synchronized boolean commitTransaction() {
        lock.lock();
        try {
            if (transactionState != TransactionState.IN_TRANSACTION) {
                log.error("Invalid state transition from {} to {}", transactionState, TransactionState.COMMITTING_TRANSACTION);
                return false;
            }
            transactionState = TransactionState.COMMITTING_TRANSACTION;
            try {
                producer.commitTransaction();
                transactionState = TransactionState.READY;
                return true;
            } catch (Exception e) {
                log.error("Failed to commit transaction", e);
                transactionState = TransactionState.ABORTABLE_ERROR;
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 中止事务
     *
     * @return 是否成功
     */
    public synchronized boolean abortTransaction() {
        lock.lock();
        try {
            if (transactionState == TransactionState.FATAL_ERROR) {
                log.error("Cannot abort transaction in fatal error state");
                return false;
            }

            if (transactionState != TransactionState.IN_TRANSACTION &&
                    transactionState != TransactionState.ABORTABLE_ERROR) {
                log.error("Invalid state for abort: {}", transactionState);
                return false;
            }

            try {
                transactionState = TransactionState.ABORTING_TRANSACTION;
                producer.abortTransaction();
                transactionState = TransactionState.READY;
                return true;
            } catch (Exception e) {
                log.error("Failed to abort transaction", e);
                transactionState = TransactionState.FATAL_ERROR;
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    public synchronized void markError() {
        transactionState = TransactionState.FATAL_ERROR;
    }

    public synchronized boolean isInTransaction() {
        return transactionState == TransactionState.IN_TRANSACTION;
    }

    public synchronized boolean isInErrorState() {
        lock.lock();
        try {
            return transactionState == TransactionState.FATAL_ERROR ||
                    transactionState == TransactionState.ABORTABLE_ERROR;
        } finally {
            lock.unlock();
        }
    }

    public synchronized void reset() {
        lock.lock();
        try {
            if (transactionState == TransactionState.IN_TRANSACTION) {
                abortTransaction();
            }
            transactionState = TransactionState.READY;
        } finally {
            lock.unlock();
        }
    }

    public synchronized boolean retryCommitTransaction() {
        lock.lock();
        try {
            if (transactionState != TransactionState.ABORTABLE_ERROR) {
                log.error("Can only retry commit from ABORTABLE_ERROR state, current state: {}", transactionState);
                return false;
            }

            try {
                producer.commitTransaction();
                transactionState = TransactionState.READY;
                return true;
            } catch (Exception e) {
                log.error("Failed to retry commit transaction", e);
                transactionState = TransactionState.FATAL_ERROR;
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    // 事务状态枚举
    public enum TransactionState {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        IN_TRANSACTION,
        COMMITTING_TRANSACTION,
        ABORTING_TRANSACTION,
        ABORTABLE_ERROR,
        FATAL_ERROR;

        private boolean isTransitionValid(TransactionState source, TransactionState target) {
            switch (target) {
                case UNINITIALIZED:
                    return source == READY || source == ABORTABLE_ERROR;
                case INITIALIZING:
                    return source == UNINITIALIZED || source == ABORTING_TRANSACTION;
                case READY:
                    return source == INITIALIZING || source == COMMITTING_TRANSACTION || source == ABORTING_TRANSACTION;
                case IN_TRANSACTION:
                    return source == READY;
                case COMMITTING_TRANSACTION:
                    return source == IN_TRANSACTION;
                case ABORTING_TRANSACTION:
                    return source == IN_TRANSACTION || source == ABORTABLE_ERROR;
                case ABORTABLE_ERROR:
                    return source == IN_TRANSACTION || source == COMMITTING_TRANSACTION || source == ABORTABLE_ERROR
                            || source == INITIALIZING;
                case FATAL_ERROR:
                default:
                    // We can transition to FATAL_ERROR unconditionally.
                    // FATAL_ERROR is never a valid starting state for any transition. So the only option is to close the
                    // producer or do purely non transactional requests.
                    return true;
            }
        }
    }
}