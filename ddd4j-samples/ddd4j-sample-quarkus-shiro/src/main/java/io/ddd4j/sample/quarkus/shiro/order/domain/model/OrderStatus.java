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
package io.ddd4j.sample.quarkus.shiro.order.domain.model;

/**
 * 订单状态枚举。
 *
 * <p>状态流转：DRAFT → PAID → SHIPPED / CANCELLED
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum OrderStatus {

    /**
     * 草稿（可添加订单行、修改）
     */
    DRAFT,
    /**
     * 已支付（可发货）
     */
    PAID,
    /**
     * 已发货（终态）
     */
    SHIPPED,
    /**
     * 已取消（终态）
     */
    CANCELLED
}