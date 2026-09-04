package io.ddd4j.sample.quarkus.goods.web.dto;

import io.ddd4j.sample.quarkus.goods.domain.Goods;
import io.ddd4j.sample.quarkus.goods.domain.GoodsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Stable HTTP representation that keeps the domain model independent of JSON concerns. */
public record GoodsResponse(Long id, String code, String name, BigDecimal price, Integer stock,
                            GoodsStatus status, LocalDateTime createTime, LocalDateTime updateTime) {

    public static GoodsResponse from(Goods goods) {
        Goods source = Objects.requireNonNull(goods, "goods must not be null");
        return new GoodsResponse(source.id(), source.getCode(), source.getName(), source.getPrice(),
                source.getStock(), source.getStatus(), source.getCreateTime(), source.getUpdateTime());
    }
}
