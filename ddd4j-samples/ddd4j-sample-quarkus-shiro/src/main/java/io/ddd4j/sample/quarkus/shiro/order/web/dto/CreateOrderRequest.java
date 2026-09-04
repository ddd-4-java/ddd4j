package io.ddd4j.sample.quarkus.shiro.order.web.dto;

/**
 * 创建订单 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
}