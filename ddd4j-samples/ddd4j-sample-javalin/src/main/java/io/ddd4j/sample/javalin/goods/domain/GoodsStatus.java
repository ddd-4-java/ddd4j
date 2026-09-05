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
package io.ddd4j.sample.javalin.goods.domain;

/**
 * 商品状态枚举。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum GoodsStatus {

    /**
     * 上架（在售）。
     */
    ON_SALE,
    /**
     * 下架（停售）。
     */
    OFF_SALE,
    /**
     * 草稿（未上架）。
     */
    DRAFT,
    /**
     * 已删除（软删）。
     */
    DELETED
}
