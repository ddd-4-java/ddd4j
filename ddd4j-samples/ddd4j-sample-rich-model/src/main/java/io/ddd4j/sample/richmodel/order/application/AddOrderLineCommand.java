package io.ddd4j.sample.richmodel.order.application;

import java.math.BigDecimal;

/**
 * 添加订单行命令。
 *
 * @param orderId     订单 ID
 * @param productId   商品 ID
 * @param productName 商品名称
 * @param quantity    购买数量
 * @param unitPrice   单价
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record AddOrderLineCommand(String orderId, String productId, String productName, int quantity, BigDecimal unitPrice) {
}
