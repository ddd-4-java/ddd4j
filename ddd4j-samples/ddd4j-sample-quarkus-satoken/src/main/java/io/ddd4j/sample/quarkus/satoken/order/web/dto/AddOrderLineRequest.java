package io.ddd4j.sample.quarkus.satoken.order.web.dto;

import java.math.BigDecimal;

/**
 * 添加订单行 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
}