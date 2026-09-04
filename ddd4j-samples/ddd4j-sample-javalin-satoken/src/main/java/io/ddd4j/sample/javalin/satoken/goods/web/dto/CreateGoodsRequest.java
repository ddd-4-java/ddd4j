package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import java.math.BigDecimal;

/**
 * 创建商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record CreateGoodsRequest(String code, String name, BigDecimal price, Integer stock) {
}