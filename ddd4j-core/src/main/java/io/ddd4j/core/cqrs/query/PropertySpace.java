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
package io.ddd4j.core.cqrs.query;

/**
 * 查询属性所属的模型空间。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public enum PropertySpace {

    /**
     * 领域模型属性，由 Repository 映射为持久化属性。
     */
    DOMAIN,

    /**
     * 持久化对象属性，仅允许在显式 PO 作用域中使用。
     */
    PERSISTENCE
}
