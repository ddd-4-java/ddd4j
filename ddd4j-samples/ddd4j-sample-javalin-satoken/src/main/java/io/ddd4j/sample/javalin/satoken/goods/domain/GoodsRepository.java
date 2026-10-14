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
package io.ddd4j.sample.javalin.satoken.goods.domain;

import io.ddd4j.core.ddd.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品仓储接口（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface GoodsRepository extends Repository<Goods, Long> {

    /**
     * 按商品编码精确查询商品。
     *
     * @param code 商品编码
     * @return 命中的商品，不存在时为空
     */
    Optional<Goods> findByCode(String code);

    /**
     * 按商品状态查询商品列表。
     *
     * @param status 商品状态
     * @return 该状态下的商品列表，无命中时为空列表
     */
    List<Goods> findByStatus(GoodsStatus status);
}
