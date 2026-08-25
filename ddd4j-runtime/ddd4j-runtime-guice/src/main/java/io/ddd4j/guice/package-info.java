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
 * ddd4j-runtime-guice：Guice 桥接。
 * <p>
 * 本模块提供 3 个核心 SPI 的 Guice 实现：DomainEventPublisher（Guava EventBus）、
 * SubjectProvider（Guice Injector）、I18nProvider（ResourceBundle）。
 * 用户通过 {@code Ddd4jGuiceModule} 一行启用全部 SPI。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
package io.ddd4j.guice;
