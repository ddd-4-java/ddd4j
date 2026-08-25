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
/**
 * ddd4j-ddd-rules-cola：COLA 架构目录规范检查。
 * <p>
 * COLA（Clean Object-oriented and Layered Architecture）是阿里巴巴推荐的 DDD 落地架构。
 * 本模块确保项目启动时符合 COLA 的分层约束：
 * <ul>
 *   <li>domain 层不依赖任何外层</li>
 *   <li>adapter 层实现 domain 定义的 gateway 接口</li>
 *   <li>application 层包含 executor（命令）或 query（查询）子包</li>
 *   <li>infrastructure 层提供框架配置和外部服务调用</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
package io.ddd4j.ddd.cola.checker;
