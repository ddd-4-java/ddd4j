package io.ddd4j.sample.order.domain.exception;

/**
 * 当前租户已经使用了此商品、规格或订单业务键。
 */
public final class DuplicateTradeKey extends RuntimeException {
    /**
     * 保留底层唯一键冲突的失败原因。
     *
     * @param cause 原始唯一键冲突
     */
    public DuplicateTradeKey(Throwable cause) {
        super("业务编号已存在", cause);
    }
}
