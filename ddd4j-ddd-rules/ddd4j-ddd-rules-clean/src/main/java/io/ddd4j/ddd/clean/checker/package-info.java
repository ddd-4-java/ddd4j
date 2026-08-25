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
 * ddd4j-ddd-rules-clean：Clean Architecture 目录规范检查。
 * <p>
 * 确保项目启动时符合 Clean Architecture 的分层约束：
 * <ul>
 *   <li>domain 层不依赖任何外层（零 Spring/MyBatis/Web 依赖）</li>
 *   <li>application 层只依赖 domain</li>
 *   <li>adapter 层实现 domain 定义的端口（Port）</li>
 *   <li>infrastructure 层提供框架配置</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
package io.ddd4j.ddd.clean.checker;
