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
package io.ddd4j.data.jpa;

import io.ddd4j.kit.lang.IdKit;

/**
 * UUID 字符串 ID 策略：生成 32 位无横线 UUID。
 *
 * <p>委托 ddd4j 标准工具 {@link IdKit#simpleUUID()}（Hutool 实现，比 JDK UUID 高性能）。
 * 适用于需要全局唯一、不依赖数据库自增、无安全顺序泄露要求的场景。
 * 使用此策略时，实体主键应为 {@code String} 类型：
 * <pre>
 *   @Id
 *   @GeneratedValue(generator = "ddd4j-uuid")
 *   @GenericGenerator(name = "ddd4j-uuid", strategy = "org.hibernate.id.UUIDGenerator")
 *   public String id;
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see IdKit
 * @since 3.3.x
 */
public class UuidIdStrategy implements IdGenerationStrategy<String> {

    @Override
    public String generate() {
        return IdKit.simpleUUID();
    }
}
