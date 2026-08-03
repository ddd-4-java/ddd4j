package io.ddd4j.sample.order.jdbc;

import io.ddd4j.sample.order.application.OutboxDispatchResult;
import io.ddd4j.sample.order.application.OutboxPublisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将 Outbox 领取、发送和状态确认封装在 JDBC 事务中的调度入口。
 *
 * <p>PostgreSQL {@code FOR UPDATE SKIP LOCKED} 依赖同一事务保持行锁；各 Runtime 的定时任务只需调用
 * {@link #publishPending(int)}，无需了解 JDBC 连接管理细节。
 */
public final class TransactionalOutboxPublisher {

    private final JdbcOrderTransactionPort transaction;
    private final OutboxPublisher publisher;

    public TransactionalOutboxPublisher(JdbcOrderTransactionPort transaction, OutboxPublisher publisher) {
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    /**
     * 在单个 PostgreSQL 事务中处理一批待发布消息。
     *
     * @param limit 本轮最多处理数量
     * @return 发布结果
     */
    public OutboxDispatchResult publishPending(int limit) {
        AtomicReference<OutboxDispatchResult> result = new AtomicReference<>();
        transaction.execute(() -> result.set(publisher.dispatchPending(limit)));
        return Objects.requireNonNull(result.get(), "outbox dispatch result must not be null");
    }
}
