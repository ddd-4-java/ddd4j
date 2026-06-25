package io.ddd4j.boot.mq.acknowledgment;

/**
 * 业务层消费结果语义，映射到 {@link MessageAcknowledgment} 底层操作。
 */
public enum AckDisposition {

    /** 成功消费 → ack(false) */
    ACK,

    /** 幂等跳过 / 终态丢弃 → ack(false) */
    DISCARD,

    /** 瞬时失败 → nack(requeue=true) 或 recover(true) */
    REQUEUE,

    /** 永久失败 → nack(requeue=false)，配合 DLQ */
    REJECT_TO_DLQ,

    /** 处理中 / 锁占用 → nack(requeue=true) */
    DEFER
}
