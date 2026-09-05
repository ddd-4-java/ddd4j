package io.ddd4j.sample.quarkus.goods.web.dto;

import java.math.BigDecimal;

/**
 * 创建商品请求 DTO。
 *
 * <p>轻量 record：与 Spring MVC 的 record 参数绑定风格一致，
 * 同时被 JAX-RS / Quarkus REST 原生支持。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record CreateGoodsRequest(String code, String name, BigDecimal price, Integer stock) {
}
