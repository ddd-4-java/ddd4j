package io.ddd4j.sample.order.domain.model;

/** 同一个订单已经进入与当前操作不兼容的状态。 */
public final class TradeOrderStateConflict extends RuntimeException {
    public TradeOrderStateConflict(String message) {
        super(message);
    }
}
