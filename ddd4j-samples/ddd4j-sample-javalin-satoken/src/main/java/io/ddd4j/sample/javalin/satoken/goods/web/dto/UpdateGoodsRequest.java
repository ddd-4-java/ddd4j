package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import java.math.BigDecimal;

/**
 * 更新商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record UpdateGoodsRequest(String name, BigDecimal price) {
}