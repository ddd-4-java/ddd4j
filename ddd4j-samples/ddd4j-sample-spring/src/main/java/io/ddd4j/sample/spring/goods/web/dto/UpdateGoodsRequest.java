package io.ddd4j.sample.spring.goods.web.dto;

import java.math.BigDecimal;

/**
 * 更新商品请求 DTO（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>采用 Java {@code record} 形式：不可变、简洁、自动生成 getter/equals/hashCode。
 * 适合"轻量"业务场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record UpdateGoodsRequest(String name, BigDecimal price) {
}
