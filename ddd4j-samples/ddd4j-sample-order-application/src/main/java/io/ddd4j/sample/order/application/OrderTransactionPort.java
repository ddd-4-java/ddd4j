package io.ddd4j.sample.order.application;

import java.util.Objects;

/**
 * 订单写侧的事务边界端口。
 *
 * <p>运行时适配器应在该边界内原子写入订单状态与 Outbox 消息；领域和应用层不依赖
 * Spring、JPA 或任何具体数据库事务 API。
 */
@FunctionalInterface
public interface OrderTransactionPort {

    /**
     * 在同一个事务边界内执行订单持久化操作。
     *
     * @param operation 订单与 Outbox 的原子写入操作
     */
    void execute(Runnable operation);

    /**
     * 返回用于非事务性测试或最小化运行时的直通实现。
     *
     * @return 不开启实际事务的事务端口
     */
    static OrderTransactionPort noop() {
        return operation -> Objects.requireNonNull(operation, "operation must not be null").run();
    }
}
