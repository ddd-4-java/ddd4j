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
package io.ddd4j.sample.quarkus.cqrs.goods.web.dto;

import java.math.BigDecimal;

/**
 * 更新商品请求 DTO。
 *
 * <p>轻量 record：与 Spring MVC 的 record 参数绑定风格一致，
 * 同时被 JAX-RS / Quarkus REST 原生支持。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record UpdateGoodsRequest(String name, BigDecimal price) {
}
