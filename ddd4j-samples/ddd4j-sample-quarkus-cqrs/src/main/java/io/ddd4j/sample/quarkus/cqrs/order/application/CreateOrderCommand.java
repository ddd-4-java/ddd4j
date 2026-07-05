package io.ddd4j.sample.quarkus.cqrs.order.application;

/**
 * 创建订单命令。
 *
 * <p>CQRS 命令端：封装创建草稿订单所需的全部参数，
 * 由 {@link OrderApplicationService#createDraft} 消费。
 *
 * @param orderNo   订单编号
 * @param buyerId   买家 ID
 * @param buyerName 买家名称
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
}
