package io.ddd4j.sample.order.domain.gateway;

/** 当前租户已经使用了此商品、规格或订单业务键。 */
public final class DuplicateTradeKey extends RuntimeException {
    public DuplicateTradeKey(Throwable cause) {
        super("业务编号已存在", cause);
    }
}
