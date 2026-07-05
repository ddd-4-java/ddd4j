package io.ddd4j.sample.quarkus.satoken.order.application;

/**
 * 创建订单命令。
 *
 * @param orderNo   订单编号
 * @param buyerId   买家 ID
 * @param buyerName 买家显示名称
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
}