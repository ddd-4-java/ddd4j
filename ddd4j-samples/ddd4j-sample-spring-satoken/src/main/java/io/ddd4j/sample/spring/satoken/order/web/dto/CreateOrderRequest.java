package io.ddd4j.sample.spring.satoken.order.web.dto;

/**
 * 创建订单 REST 请求。
 *
 * @param orderNo   订单编号
 * @param buyerId   买家 ID
 * @param buyerName 买家显示名称
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
}
