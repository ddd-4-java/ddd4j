package io.ddd4j.sample.quarkus.cqrs.order.application;

import java.math.BigDecimal;

/**
 * 添加订单行命令。
 *
 * <p>CQRS 命令端：封装向已有订单添加商品行所需的全部参数，
 * 由 {@link OrderApplicationService#addLine} 消费。
 *
 * @param orderId   订单 ID
 * @param goodsId   商品 ID
 * @param goodsName 商品名称
 * @param quantity  数量
 * @param unitPrice 单价
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record AddOrderLineCommand(String orderId, String goodsId, String goodsName,
                                  int quantity, BigDecimal unitPrice) {
}
