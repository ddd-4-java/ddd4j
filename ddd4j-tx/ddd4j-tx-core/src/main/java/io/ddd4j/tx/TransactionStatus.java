package io.ddd4j.tx;

/**
 * 分布式事务状态枚举。
 *
 * @author hiwepy
 * @since 4.0.0
 */
public enum TransactionStatus {

    /**
     * 事务已开启
     */
    ACTIVE,

    /**
     * 事务已提交
     */
    COMMITTED,

    /**
     * 事务已回滚
     */
    ROLLED_BACK,

    /**
     * 事务正在提交中（二阶段提交中间状态）
     */
    COMMITTING,

    /**
     * 事务正在回滚中
     */
    ROLLING_BACK,

    /**
     * 事务已完成（最终状态）
     */
    FINISHED,

    /**
     * 事务未知状态
     */
    UNKNOWN
}
