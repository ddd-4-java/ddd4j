package io.ddd4j.tx.outbox;

import io.ddd4j.mq.delivery.MQOutboxDispatcher;
import io.ddd4j.mq.delivery.MQOutboxRecord;
import io.ddd4j.mq.delivery.MQOutboxStore;
import io.ddd4j.tx.TransactionPort;

import java.util.List;
import java.util.Objects;

/**
 * 事务性 Outbox 端口。
 *
 * <p>将 Outbox 消息追加与业务操作绑定在同一事务中，保证原子性：
 * <ul>
 *   <li>业务数据变更 + Outbox 消息追加在同一事务中</li>
 *   <li>事务提交后，{@link MQOutboxDispatcher} 异步发送消息到 Broker</li>
 *   <li>事务回滚时，Outbox 消息也回滚</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * TransactionalOutbox outbox = new TransactionalOutbox(transactionPort, outboxStore);
 * outbox.executeWithOutbox(() -> {
 *     order.save();
 *     outbox.append(new MQOutboxRecord(...));
 * });
 * }</pre>
 *
 * @author hiwepy
 * @since 4.0.0
 * @see TransactionPort
 * @see MQOutboxStore
 * @see MQOutboxDispatcher
 */
public class TransactionalOutbox {

    private final TransactionPort transactionPort;
    private final MQOutboxStore outboxStore;

    public TransactionalOutbox(TransactionPort transactionPort, MQOutboxStore outboxStore) {
        this.transactionPort = Objects.requireNonNull(transactionPort, "transactionPort must not be null");
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore must not be null");
    }

    /**
     * 在事务内执行操作并追加 Outbox 消息。
     *
     * <p>保证业务操作和 Outbox 消息追加在同一事务中。
     *
     * @param operation 事务内的业务操作（包含 outbox.append 调用）
     */
    public void executeWithOutbox(Runnable operation) {
        transactionPort.execute(operation);
    }

    /**
     * 追加 Outbox 消息（必须在事务内调用）。
     *
     * @param record Outbox 记录
     */
    public void append(MQOutboxRecord record) {
        outboxStore.append(record);
    }

    /**
     * 批量追加 Outbox 消息（必须在事务内调用）。
     *
     * @param records Outbox 记录列表
     */
    public void appendAll(List<MQOutboxRecord> records) {
        for (MQOutboxRecord record : records) {
            outboxStore.append(record);
        }
    }

    /**
     * 获取底层事务端口。
     */
    public TransactionPort getTransactionPort() {
        return transactionPort;
    }

    /**
     * 获取底层 Outbox 存储。
     */
    public MQOutboxStore getOutboxStore() {
        return outboxStore;
    }
}
