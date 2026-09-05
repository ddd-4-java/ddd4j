/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
