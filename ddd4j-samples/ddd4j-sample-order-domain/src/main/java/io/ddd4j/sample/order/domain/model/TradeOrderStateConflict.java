package io.ddd4j.sample.order.domain.model;

/** 同一个订单已经进入与当前操作不兼容的状态。 */
public final class TradeOrderStateConflict extends RuntimeException {
    /**
     * 构建非法状态迁移的业务异常。
     *
     * @param message 状态冲突说明
     */
    public TradeOrderStateConflict(String message) {
        super(message);
    }
}
