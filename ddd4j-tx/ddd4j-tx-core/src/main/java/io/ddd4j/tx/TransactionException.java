package io.ddd4j.tx;

/**
 * 分布式事务异常。
 *
 * @author hiwepy
 * @since 4.0.0
 */
public class TransactionException extends RuntimeException {

    private final TransactionStatus status;

    public TransactionException(String message) {
        super(message);
        this.status = TransactionStatus.UNKNOWN;
    }

    public TransactionException(String message, Throwable cause) {
        super(message, cause);
        this.status = TransactionStatus.UNKNOWN;
    }

    public TransactionException(String message, TransactionStatus status) {
        super(message);
        this.status = status;
    }

    public TransactionException(String message, Throwable cause, TransactionStatus status) {
        super(message, cause);
        this.status = status;
    }

    public TransactionStatus getStatus() {
        return status;
    }
}
