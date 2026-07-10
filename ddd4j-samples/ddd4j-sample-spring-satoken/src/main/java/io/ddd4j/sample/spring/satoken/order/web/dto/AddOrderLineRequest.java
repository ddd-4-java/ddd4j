package io.ddd4j.sample.spring.satoken.order.web.dto;

import java.math.BigDecimal;

/**
 * 添加订单行 REST 请求。
 *
 * @param goodsId   商品 ID
 * @param goodsName 商品名称
 * @param quantity  数量
 * @param unitPrice 单价
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
}
