package io.ddd4j.sample.dropwizard.cqrs.command;

/**
 * 创建订单命令（CQRS 写侧）。
 *
 * @param orderNo   订单编号
 * @param buyerId   买家 ID
 * @param buyerName 买家名称
 */
public record CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
}
