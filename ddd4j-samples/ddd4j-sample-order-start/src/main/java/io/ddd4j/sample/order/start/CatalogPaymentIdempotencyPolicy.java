package io.ddd4j.sample.order.start;

import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.idempotency.ApplicationIdempotencyPolicy;

import java.util.Objects;
import java.util.regex.Pattern;

/** 将支付确认的重复请求交给四表交易事务，由锁定的订单快照判定重试或金额冲突。 */
public final class CatalogPaymentIdempotencyPolicy implements ApplicationIdempotencyPolicy {
    /** 两种商品交易 HTTP 路由，前面可含部署的上下文路径。 */
    private static final Pattern PAYMENT = Pattern.compile("(?:/[^/]+)*/(?:api|orders/catalog)/orders/[^/]+/payment");

    /** 创建商品交易示例的支付重试策略。 */
    public CatalogPaymentIdempotencyPolicy() {
    }

    /**
     * 仅将商品交易的支付确认 POST 路由交给订单事务处理。
     *
     * @param context 包含请求方法和路径的当前上下文
     * @return 命中商品交易支付确认路由时为 true
     */
    @Override
    public boolean handles(WebRequestContext context) {
        return "POST".equals(context.method()) && Objects.nonNull(context.path())
                && PAYMENT.matcher(context.path()).matches();
    }
}
